package aqua.blatt1.client;

import aqua.blatt1.common.FishModel;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ToggleFishColor implements ActionListener {
    TankModel tankModel;
    String fishId;

    public ToggleFishColor(TankModel tankModel, String fishId) {
        this.tankModel = tankModel;
        this.fishId = fishId;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        tankModel.locateFishGlobally(fishId);
    }
}
