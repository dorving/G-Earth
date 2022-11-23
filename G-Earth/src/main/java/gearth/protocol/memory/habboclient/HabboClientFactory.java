package gearth.protocol.memory.habboclient;

import gearth.misc.OSValidator;
import gearth.protocol.HConnection;
import gearth.protocol.memory.habboclient.linux.LinuxHabboClient;
import gearth.protocol.memory.habboclient.macOs.MacOsHabboClient;
import gearth.protocol.memory.habboclient.windows.WindowsHabboClient;

import java.util.function.Function;

/**
 * Created by Jonas on 13/06/18.
 */
public class HabboClientFactory {

    public static Function<HConnection, HabboClient> clientProvider = hConnection -> {
        if (OSValidator.isUnix()) return new LinuxHabboClient(hConnection);
        if (OSValidator.isWindows()) return new WindowsHabboClient(hConnection);
        if (OSValidator.isMac()) return new MacOsHabboClient(hConnection);
        return null;
    };

    public static HabboClient get(HConnection connection) {
        // todo use rust if beneficial
        return clientProvider.apply(connection);
    }
}
