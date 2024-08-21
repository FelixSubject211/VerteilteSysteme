package aqua.blatt1.broker;

import aqua.blatt1.common.Properties;
import aqua.blatt1.common.msgtypes.*;
import aqua.blatt1.poisoner.PoisonPill;
import messaging.Endpoint;
import messaging.Message;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class Broker {
    private final ClientCollection<InetSocketAddress> clientCollection;
    private final ExecutorService executorService;
    private final Endpoint endpoint;
    private final ReadWriteLock lock;

    private volatile boolean stopRequested = false;

    public static void main(String[] args) {
        Broker broker = new Broker();
        Runtime.getRuntime().addShutdownHook(new Thread(broker::shutdown));
    }

    public Broker() {
        this.clientCollection = new ClientCollection<>();
        this.executorService = Executors.newFixedThreadPool(Properties.THREAD_POOL_SIZE);
        this.endpoint = new Endpoint(Properties.PORT);
        this.lock = new ReentrantReadWriteLock();

        new Receiver().start();
        new StopGuiThread(v -> shutdown()).start();
    }

    public void shutdown() {
        stopRequested = true;
        executorService.shutdown();
    }

    public class Receiver extends Thread {
        @Override
        public void run() {
            while (!isInterrupted() && !stopRequested) {
                Message msg = endpoint.blockingReceive();
                executorService.submit(new BrokerTask(msg));
            }
            System.out.println("Receiver stopped.");
        }
    }

    public class BrokerTask implements Runnable {
        private final Message msg;

        public BrokerTask(Message msg) {
            this.msg = msg;
        }

        @Override
        public void run() {
            Serializable content = msg.getPayload();

            if (content instanceof PoisonPill) {
                shutdown();
            } else if (content instanceof RegisterRequest) {
                handleRegisterRequest(msg.getSender());
            } else if (content instanceof DeregisterRequest deregisterRequest) {
                handleDeregisterRequest(deregisterRequest);
            } else if (content instanceof HandoffRequest handoffRequest) {
                handleHandoffRequest(handoffRequest, msg.getSender());
            } else {
                System.out.println("Unknown message type: " + content.getClass().getName());
            }
        }

        private void handleRegisterRequest(InetSocketAddress address) {
            String id = UUID.randomUUID().toString();
            lock.writeLock().lock();
            clientCollection.add(id, address);

            int clientIndex = clientCollection.indexOf(address);
            InetSocketAddress client = clientCollection.getClient(clientIndex);
            InetSocketAddress left = clientCollection.getLeftNeighorOf(clientIndex);
            InetSocketAddress right = clientCollection.getRightNeighorOf(clientIndex);

            lock.writeLock().unlock();

            endpoint.send(address, new RegisterResponse(id,left,right));
            endpoint.send(left, new NeighborUpdate(null, client));
            endpoint.send(right, new NeighborUpdate(client, null));
        }

        private void handleDeregisterRequest(DeregisterRequest request) {
            lock.writeLock().lock();
            int clientIndex = clientCollection.indexOf(request.getId());
            if (clientIndex == -1) {
                return;
            }

            InetSocketAddress left = clientCollection.getLeftNeighorOf(clientIndex);
            InetSocketAddress right = clientCollection.getRightNeighorOf(clientIndex);

            endpoint.send(left, new NeighborUpdate(null, right));
            endpoint.send(right, new NeighborUpdate(left, null));

            clientCollection.remove(clientIndex);
            lock.writeLock().unlock();
        }

        private void handleHandoffRequest(HandoffRequest request, InetSocketAddress address) {
            lock.readLock().lock();
            switch (request.getFish().getDirection()) {
                case LEFT:
                    InetSocketAddress leftNeighbor = clientCollection.getLeftNeighorOf(clientCollection.indexOf(address));
                    endpoint.send(leftNeighbor, request);
                    break;
                case RIGHT:
                    InetSocketAddress rightNeighbor = clientCollection.getRightNeighorOf(clientCollection.indexOf(address));
                    endpoint.send(rightNeighbor, request);
                    break;
                default:
                    System.out.println("Unknown fish direction: " + request.getFish().getDirection());
            }
            lock.readLock().unlock();
        }
    }
}