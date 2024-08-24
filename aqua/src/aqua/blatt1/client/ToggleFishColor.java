package aqua.blatt1.client;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.rmi.RemoteException;

public class ToggleFishColor implements ActionListener {
    TankModel tankModel;
    String fishId;

    public ToggleFishColor(TankModel tankModel, String fishId) {
        this.tankModel = tankModel;
        this.fishId = fishId;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            tankModel.locationRequest(fishId);
        } catch (RemoteException ignored) {}
    }
}
