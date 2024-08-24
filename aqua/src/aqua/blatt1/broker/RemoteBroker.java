package aqua.blatt1.broker;

import aqua.blatt1.client.RemoteClient;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemoteBroker extends Remote {
    public void registerRequest(RemoteClient client) throws RemoteException;

    public void deregisterRequest(RemoteClient client) throws RemoteException;

    public void nameResolutionRequest(RemoteClient address, String tankId, String id) throws RemoteException;
}
