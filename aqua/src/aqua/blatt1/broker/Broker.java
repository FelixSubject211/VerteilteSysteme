package aqua.blatt1.broker;

import aqua.blatt1.client.RemoteClient;
import aqua.blatt1.common.Properties;

import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class Broker implements RemoteBroker {
    @SuppressWarnings("FieldCanBeLocal")
    private final int CLIENT_REFRESH_INTERVAL_SECONDS = 2;
    @SuppressWarnings("FieldCanBeLocal")
    private final int CLIENT_EXPIRATION_THRESHOLD_SECONDS = 3;
    @SuppressWarnings("FieldCanBeLocal")
    private final int CLIENT_CLEANUP_CHECK_INTERVAL_SECONDS = 2;

    private final ClientCollection<RemoteClient> clientCollection;
    private final ReadWriteLock lock;
    private final Timer timer;

    public static void main(String[] args) throws RemoteException, AlreadyBoundException {
        Registry registry = LocateRegistry.createRegistry(Registry.REGISTRY_PORT);
        RemoteBroker stub = (RemoteBroker) UnicastRemoteObject.exportObject(new Broker(), 0);
        registry.bind(Properties.BROKER_NAME, stub);
    }

    public Broker() {
        this.clientCollection = new ClientCollection<>(Duration.ofSeconds(CLIENT_EXPIRATION_THRESHOLD_SECONDS));
        this.lock = new ReentrantReadWriteLock();
        this.timer = new Timer();

        scheduleClientCleanup();
    }

    public void scheduleClientCleanup() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                lock.writeLock().lock();

                clientCollection.expiredClients().forEach(client -> {
                    int index = clientCollection.indexOf(client);
                    try {
                        deregisterRequest(clientCollection.getClient(index));
                    } catch (RemoteException ignored) {}
                });
                lock.writeLock().unlock();

                scheduleClientCleanup();
            }
        }, CLIENT_CLEANUP_CHECK_INTERVAL_SECONDS * 1000L);
    }


    @Override
    public void registerRequest(RemoteClient client) throws RemoteException {
        lock.writeLock().lock();

        int clientIndex = clientCollection.indexOf(client);
        boolean isExistingClient = (clientIndex != -1);

        if (isExistingClient) {
            clientCollection.refreshExpirationDuration(clientIndex);
            lock.writeLock().unlock();
            sendRegisterResponse(client, null, clientIndex);
        } else {
            String id = UUID.randomUUID().toString();
            clientCollection.add(id, client);
            clientIndex = clientCollection.indexOf(client);

            sendRegisterResponse(client, id, clientIndex);

            RemoteClient newClient = clientCollection.getClient(clientIndex);
            RemoteClient left = clientCollection.getLeftNeighorOf(clientIndex);
            RemoteClient right = clientCollection.getRightNeighorOf(clientIndex);

            lock.writeLock().unlock();

            left.neighborUpdate(null, newClient);
            right.neighborUpdate(newClient, null);
        }
    }

    private void sendRegisterResponse(RemoteClient address, String id, int clientIndex) {
        RemoteClient left = clientCollection.getLeftNeighorOf(clientIndex);
        RemoteClient right = clientCollection.getRightNeighorOf(clientIndex);
        boolean isFirstClient = clientCollection.size() == 1;
        try {
            address.registerResponse(id, left, right, isFirstClient, CLIENT_REFRESH_INTERVAL_SECONDS);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void deregisterRequest(RemoteClient client) throws RemoteException {
        lock.writeLock().lock();
        int clientIndex = clientCollection.indexOf(client);
        if (clientIndex == -1) {
            lock.writeLock().unlock();
            return;
        }

        RemoteClient left = clientCollection.getLeftNeighorOf(clientIndex);
        RemoteClient right = clientCollection.getRightNeighorOf(clientIndex);

        left.neighborUpdate(null, right);
        right.neighborUpdate(left, null);

        clientCollection.remove(clientIndex);
        lock.writeLock().unlock();
    }

    @Override
    public void nameResolutionRequest(RemoteClient address, String tankId, String id) throws RemoteException {
        lock.readLock().lock();
        int index = clientCollection.indexOf(tankId);
        if (index == -1) {
            lock.readLock().unlock();
            return;
        }
        RemoteClient target = clientCollection.getClient(index);
        target.nameResolutionResponse(address, id);
        lock.readLock().unlock();
    }
}