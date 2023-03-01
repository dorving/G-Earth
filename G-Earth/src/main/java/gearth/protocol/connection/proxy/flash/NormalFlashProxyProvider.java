package gearth.protocol.connection.proxy.flash;

import gearth.misc.Cacher;
import gearth.protocol.HConnection;
import gearth.protocol.connection.*;
import gearth.protocol.connection.proxy.ProxyProviderFactory;
import gearth.protocol.connection.proxy.SocksConfiguration;
import gearth.protocol.hostreplacer.hostsfile.HostReplacer;
import gearth.protocol.hostreplacer.hostsfile.HostReplacerFactory;
import gearth.protocol.portchecker.PortChecker;
import gearth.protocol.portchecker.PortCheckerFactory;
import gearth.ui.titlebar.TitleBarController;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class NormalFlashProxyProvider extends FlashProxyProvider {

    private List<String> potentialHosts;

    private static final HostReplacer hostsReplacer = HostReplacerFactory.get();
    private volatile boolean hostRedirected = false;

    private volatile List<HProxy> potentialProxies = new ArrayList<>();
    private volatile HProxy proxy = null;

    private boolean useSocks;


    public NormalFlashProxyProvider(HProxySetter proxySetter, HStateSetter stateSetter, HConnection hConnection, List<String> potentialHosts, boolean useSocks) {
        super(proxySetter, stateSetter, hConnection);
        this.potentialHosts = potentialHosts;
        this.useSocks = useSocks;
    }


    @Override
    public void start() throws IOException {
        if (hConnection.getState() != HState.NOT_CONNECTED) {
            return;
        }

        prepare();
        addToHosts();
        launchProxy();

    }

    private void prepare() {
        stateSetter.setState(HState.PREPARING);

        List<String> willremove = new ArrayList<>();
        int c = 0;
        for (String host : potentialHosts) {
            String[] split = host.split(":");
            String input_dom = split[0];
            if (!ProxyProviderFactory.hostIsIpAddress(input_dom)) {
                int port = Integer.parseInt(split[1]);
                String actual_dom;

                InetAddress address;
                try {
                    address = InetAddress.getByName(input_dom);
                    actual_dom = address.getHostAddress();
                }
                catch (UnknownHostException e) {
                    willremove.add(host);
                    continue;
                }

                int intercept_port = port;
                String intercept_host = "127.0." + (c / 254) + "." + (1 + c % 254);
                potentialProxies.add(new HProxy(HClient.FLASH, input_dom, actual_dom, port, intercept_port, intercept_host));
                c++;
            }
        }

        List<Object> additionalCachedHotels = Cacher.getList(ProxyProviderFactory.HOTELS_CACHE_KEY);
        if (additionalCachedHotels != null) {
            for (String host : willremove) {
                additionalCachedHotels.remove(host);
            }
            Cacher.put(ProxyProviderFactory.HOTELS_CACHE_KEY, additionalCachedHotels);
        }

        stateSetter.setState(HState.PREPARED);
    }

    private void launchProxy() throws IOException {
        stateSetter.setState(HState.WAITING_FOR_CLIENT);

        for (int c = 0; c < potentialProxies.size(); c++) {
            HProxy potentialProxy = potentialProxies.get(c);

            ServerSocket proxy_server;
            try {
                proxy_server = new ServerSocket(potentialProxy.getIntercept_port(), 10, InetAddress.getByName(potentialProxy.getIntercept_host()));
            } catch (BindException e) {
                PortChecker portChecker = PortCheckerFactory.getPortChecker();
                String processName = portChecker.getProcessUsingPort(potentialProxy.getIntercept_port());
                Platform.runLater(() -> {
                    Alert a = new Alert(Alert.AlertType.ERROR, "The port is in use by " + processName,
                            ButtonType.OK);
                    try {
                        TitleBarController.create(a).showAlertAndWait();
                    } catch (IOException ex) {
                        logger.error("Failed to create port in use error alert", ex);
                    }
                });
                throw new IOException(e);
            }
            potentialProxy.initProxy(proxy_server);

            new Thread(() -> {
                try  {
                    Thread.sleep(30);
                    while ((hConnection.getState() == HState.WAITING_FOR_CLIENT) && !proxy_server.isClosed())	{
                        try {
                            Socket client = proxy_server.accept();
                            proxy = potentialProxy;
                            closeAllProxies(proxy);
                            logger.debug("Accepted proxy {}, starting proxy thread (useSocks={})",proxy, useSocks);
                            new Thread(() -> {
                                try {
                                    Socket server;
                                    if (!useSocks) {
                                         server = new Socket(proxy.getActual_domain(), proxy.getActual_port());
                                    }
                                    else {
                                        SocksConfiguration configuration = ProxyProviderFactory.getSocksConfig();
                                        if (configuration == null) {
                                            showInvalidConnectionError();
                                            abort();
                                            return;
                                        }
                                        server = configuration.createSocket();
                                        server.connect(new InetSocketAddress(proxy.getActual_domain(), proxy.getActual_port()), 5000);
                                    }

                                    startProxyThread(client, server, proxy);
                                } catch (SocketException | SocketTimeoutException e) {
                                    // should only happen when SOCKS configured badly
                                    showInvalidConnectionError();
                                    abort();
                                    logger.error("Failed to configure SOCKS proxy", e);
                                }
                                catch (InterruptedException | IOException e) {
                                    logger.error("An unexpected exception occurred", e);
                                }
                            }).start();
                        } catch (IOException e1) {
                            logger.error("An unexpected exception occurred", e1);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
        logger.debug("Done waiting for clients with connection state {}", hConnection.getState());
    }

    @Override
    public void abort() {
        stateSetter.setState(HState.ABORTING);
        if (hostRedirected)	{
            removeFromHosts();
        }

        clearAllProxies();
        super.abort();
    }

    @Override
    protected void onConnect() {
        super.onConnect();
        removeFromHosts();
        clearAllProxies();
    }

    private void addToHosts() {
        List<String> linesTemp = new ArrayList<>();
        for (HProxy proxy : potentialProxies) {
            linesTemp.add(proxy.getIntercept_host() + " " + proxy.getInput_domain());
        }

        String[] lines = new String[linesTemp.size()];
        for (int i = 0; i < linesTemp.size(); i++) {
            lines[i] = linesTemp.get(i);
        }
        hostsReplacer.addRedirect(lines);
        hostRedirected = true;
    }
    private void removeFromHosts(){
        List<String> linesTemp = new ArrayList<>();
        for (HProxy proxy : potentialProxies) {
            linesTemp.add(proxy.getIntercept_host() + " " + proxy.getInput_domain());
        }

        String[] lines = new String[linesTemp.size()];
        for (int i = 0; i < linesTemp.size(); i++) {
            lines[i] = linesTemp.get(i);
        }
        hostsReplacer.removeRedirect(lines);
        hostRedirected = false;
    }

    private void clearAllProxies() {
        closeAllProxies(null);
//        potentialProxies = new ArrayList<>();
    }
    private void closeAllProxies(HProxy except) {
        for (HProxy proxy : potentialProxies) {
            if (except != proxy) {
                if (proxy.getProxy_server() != null && !proxy.getProxy_server().isClosed())	{
                    try {
                        proxy.getProxy_server().close();
                    } catch (IOException e) {
                        logger.error("Failed to close all proxies", e);
                    }
                }
            }
        }
    }
}
