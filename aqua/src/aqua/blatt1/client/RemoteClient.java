package aqua.blatt1.client;

import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.*;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemoteClient extends Remote {
    public void registerResponse(
            String id,
            RemoteClient left,
            RemoteClient right,
            boolean hasToken,
            int leaseDuration
    ) throws RemoteException;

    public void handoffRequest(FishModel fish) throws RemoteException;

    public void token() throws RemoteException;

    public void neighborUpdate(RemoteClient leftOrNull, RemoteClient rightOrNull) throws RemoteException;

    public void snapshotMarker(RemoteClient sender) throws RemoteException;

    public void snapshotToken(int count) throws RemoteException;

    public void locationRequest(String fishId) throws RemoteException;

    public void nameResolutionResponse(RemoteClient address, String id) throws RemoteException;
}
