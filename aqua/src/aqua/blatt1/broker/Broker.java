package aqua.blatt1.broker;

import aqua.blatt1.common.Properties;
import aqua.blatt1.common.msgtypes.DeregisterRequest;
import aqua.blatt1.common.msgtypes.HandoffRequest;
import aqua.blatt1.common.msgtypes.RegisterRequest;
import aqua.blatt1.common.msgtypes.RegisterResponse;
import messaging.Endpoint;
import messaging.Message;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.UUID;

public class Broker {
    private final ClientCollection<InetSocketAddress> clientCollection;

    public static void main(String[] args) {
        new Broker();
    }

    public Broker() {
        this.clientCollection = new ClientCollection<>();

        new Receiver().start();
    }

    public class Receiver extends Thread {
        private final Endpoint endpoint = new Endpoint(Properties.PORT);

        @Override
        public void run() {
            while (!isInterrupted()) {
                Message msg = endpoint.blockingReceive();
                Serializable content = msg.getPayload();

                System.out.println(clientCollection.size());

                if (content instanceof RegisterRequest) {
                    handleRegisterRequest(msg.getSender());
                } else if (content instanceof DeregisterRequest deregisterRequest) {
                    handleDeregisterRequest(deregisterRequest);
                } else if (content instanceof HandoffRequest handoffRequest) {
                    handleHandoffRequest(handoffRequest, msg.getSender());
                } else {
                    System.out.println("Unknown message type: " + content.getClass().getName());
                }
            }
            System.out.println("Receiver stopped.");
        }

        private void handleRegisterRequest(InetSocketAddress address) {
            String id = UUID.randomUUID().toString();
            clientCollection.add(id, address);
            endpoint.send(address, new RegisterResponse(id));
        }

        private void handleDeregisterRequest(DeregisterRequest request) {
            int index = clientCollection.indexOf(request.getId());
            if (index != -1) {
                clientCollection.remove(index);
            }
        }

        private void handleHandoffRequest(HandoffRequest request, InetSocketAddress address) {
            switch (request.getFish().getDirection()) {
                case LEFT:
                    InetSocketAddress leftNeighor = clientCollection.getLeftNeighorOf(clientCollection.indexOf(address));
                    endpoint.send(leftNeighor, request);
                    break;
                case RIGHT:
                    InetSocketAddress rightNeighor = clientCollection.getRightNeighorOf(clientCollection.indexOf(address));
                    endpoint.send(rightNeighor, request);
                    break;
                default: System.out.println("Unknown fish direction: " + request.getFish().getDirection());
            }
        }
    }
}