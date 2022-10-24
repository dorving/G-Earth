package gearth.services.extension_handler.extensions.implementations.network;

import gearth.Configuration;
import gearth.ConfigurationKt;
import gearth.protocol.HPacket;
import gearth.services.extension_handler.extensions.extensionproducers.ExtensionProducer;
import gearth.services.extension_handler.extensions.extensionproducers.ExtensionProducerObserver;
import gearth.services.extension_handler.extensions.implementations.network.authentication.Authenticator;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by Jonas on 21/06/18.
 */
public class NetworkExtensionsProducer implements ExtensionProducer {

    public static int extensionPort = -1;


    private ServerSocket serverSocket;

    @Override
    public void startProducing(ExtensionProducerObserver observer) {
//        serverSocket = new ServerSocket(0);
        int port = 9092;
        boolean serverSetup = false;
        while (!serverSetup) {
            serverSetup = createServer(port);
            port++;
        }

        final Configuration configuration = ConfigurationKt.configuration(port);

        new Thread(() -> {
            try {
                while (!serverSocket.isClosed()) {
                    Socket extensionSocket = serverSocket.accept();
                    extensionSocket.setTcpNoDelay(true);

                    new Thread(() -> {
                        try {
                            synchronized (extensionSocket) {
                                extensionSocket.getOutputStream().write((new HPacket(NetworkExtensionInfo.OUTGOING_MESSAGES_IDS.INFOREQUEST)).toBytes());
                            }

                            InputStream inputStream = extensionSocket.getInputStream();
                            DataInputStream dIn = new DataInputStream(inputStream);

                            while (!extensionSocket.isClosed()) {

                                int length = dIn.readInt();
                                byte[] headerandbody = new byte[length + 4];

                                int amountRead = 0;
                                while (amountRead < length) {
                                    amountRead += dIn.read(headerandbody, 4 + amountRead, Math.min(dIn.available(), length - amountRead));
                                }

                                HPacket packet = new HPacket(headerandbody);
                                packet.fixLength();

                                if (packet.headerId() == NetworkExtensionInfo.INCOMING_MESSAGES_IDS.EXTENSIONINFO) {
                                    NetworkExtension gEarthExtension = new NetworkExtension(
                                            packet,
                                            extensionSocket
                                    );

                                    if (Authenticator.evaluate(gEarthExtension)) {
                                        observer.onExtensionProduced(gEarthExtension);
                                    }
                                    else {
                                        gEarthExtension.close();
                                    }

                                    break;
                                }
                            }

                        } catch (IOException ignored) {}
                    }).start();
                }
            } catch (IOException e) {e.printStackTrace();}
        }).start();
    }

    private boolean createServer(int port) {
        try {
            serverSocket = new ServerSocket(port);
            extensionPort = port;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }
}
