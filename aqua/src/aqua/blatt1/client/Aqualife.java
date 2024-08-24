package aqua.blatt1.client;

import aqua.blatt1.broker.RemoteBroker;
import aqua.blatt1.common.Properties;

import javax.swing.SwingUtilities;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Aqualife {

	public static void main(String[] args) throws NotBoundException, RemoteException {
		Registry registry = LocateRegistry.getRegistry();
		RemoteBroker broker = (RemoteBroker) registry.lookup(Properties.BROKER_NAME);
		TankModel tankModel = new TankModel(broker);
		SwingUtilities.invokeLater(new AquaGui(tankModel));
		tankModel.run();
	}
}
