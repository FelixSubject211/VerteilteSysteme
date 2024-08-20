package aqua.blatt1.broker;

import javax.swing.*;
import java.util.function.Consumer;

public class StopGuiThread extends Thread {
    private final Consumer<Void> stopCallback;

    public StopGuiThread(Consumer<Void> stopCallback) {
        this.stopCallback = stopCallback;
    }

    @Override
    public void run() {
        JFrame frame = new JFrame("Broker Control");
        JButton stopButton = new JButton("Stop Server");

        stopButton.addActionListener(e -> {
            stopCallback.accept(null);
            frame.dispose();
        });

        frame.getContentPane().add(stopButton);
        frame.setSize(200, 100);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}
