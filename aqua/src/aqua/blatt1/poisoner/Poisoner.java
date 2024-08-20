package aqua.blatt1.poisoner;

import aqua.blatt1.common.Properties;
import messaging.Endpoint;

import javax.swing.*;
import java.net.InetSocketAddress;

public class Poisoner {
    private final Endpoint endpoint;
    private final InetSocketAddress broker;

    public Poisoner() {
        this.endpoint = new Endpoint();
        this.broker = new InetSocketAddress(Properties.HOST, Properties.PORT);
    }

    public void sendPoison() {
        endpoint.send(broker, new PoisonPill());
    }

    public static void main(String[] args) {
        JOptionPane.showMessageDialog(null, "Press OK button to poison server.");
        new Poisoner().sendPoison();
    }
}
