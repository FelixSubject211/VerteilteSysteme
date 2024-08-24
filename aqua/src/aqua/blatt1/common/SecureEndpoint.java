package aqua.blatt1.common;


import messaging.Endpoint;
import messaging.Message;
import javax.crypto.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.security.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SecureEndpoint extends Endpoint {
    private static final String ALGORITHM = "RSA";
    private final Lock lock = new ReentrantLock();
    private final Map<InetSocketAddress, Key> keys = new HashMap<>();
    private KeyPair keyPair;

    public SecureEndpoint() {
        super();
        createKeyPair();
    }
    public SecureEndpoint(int port) {
        super(port);
        createKeyPair();
    }

    private void createKeyPair() {
        try {
            KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance(ALGORITHM);
            keyPairGen.initialize(4096);
            this.keyPair = keyPairGen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void send(InetSocketAddress address, Serializable payload) {
        lock.lock();
        if(!this.keys.containsKey(address)) {
            super.send(address, new KeyExchangeMessage(keyPair.getPublic()));
            Message message = super.blockingReceive();
            if (message.getPayload() instanceof KeyExchangeMessage) {
                keys.put(message.getSender(),((KeyExchangeMessage) message.getPayload()).key());
                encrypt(address,payload);
            }
        } else {
            encrypt(address, payload);
        }
        lock.unlock();
    }

    private void encrypt(InetSocketAddress address, Serializable payload) {
        try {
            Cipher encryptCipher = Cipher.getInstance(ALGORITHM);
            encryptCipher.init(Cipher.ENCRYPT_MODE, this.keys.get(address));
            byte[] encrypted = encryptCipher.doFinal(toBytes(payload));
            super.send(address, encrypted);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] toBytes(Object object) throws IOException {
        try (ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(arrayOutputStream)) {
            objectOutputStream.writeObject(object);
            return arrayOutputStream.toByteArray();
        }
    }

    private Object fromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
        return objectInputStream.readObject();
    }

    private Message decrypt(Message encryptedMessage) {
        try {
            Cipher decryptCipher = Cipher.getInstance(ALGORITHM);
            decryptCipher.init(Cipher.DECRYPT_MODE, this.keyPair.getPrivate());
            byte[] decrypted = decryptCipher.doFinal((byte[]) encryptedMessage.getPayload());
            return new Message((Serializable) fromBytes(decrypted), encryptedMessage.getSender());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void sendRsaKeyBack(Message encryptedMessage) {
        if (!this.keys.containsKey(encryptedMessage.getSender())) {
            keys.put(encryptedMessage.getSender(),((KeyExchangeMessage) encryptedMessage.getPayload()).key());
        }
        super.send(encryptedMessage.getSender(), new KeyExchangeMessage(this.keyPair.getPublic()));
    }

    @Override
    public Message blockingReceive() {
        Message encryptedMessage = super.blockingReceive();
        if (encryptedMessage.getPayload() instanceof KeyExchangeMessage) {
            sendRsaKeyBack(encryptedMessage);
            return blockingReceive();
        }
        return decrypt(encryptedMessage);
    }

    @Override
    public Message nonBlockingReceive() {
        Message encryptedMessage = super.nonBlockingReceive();
        if (encryptedMessage.getPayload() instanceof KeyExchangeMessage) {
            sendRsaKeyBack(encryptedMessage);
            return nonBlockingReceive();
        }
        return decrypt(encryptedMessage);
    }

    public record KeyExchangeMessage(Key key) implements Serializable { }
}