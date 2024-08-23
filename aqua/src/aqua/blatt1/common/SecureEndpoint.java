package aqua.blatt1.common;

import messaging.Endpoint;
import messaging.Message;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.security.Key;

public class SecureEndpoint extends Endpoint {
    private static final String KEY_STRING = "CAFEBABECAFEBABE";
    private static final String ALGORITHM = "AES";
    private final Key key;
    private final Cipher encryptCipher;
    private final Cipher decryptCipher;

    public SecureEndpoint() {
        super();
        key = generateKey();
        encryptCipher = createCipher(Cipher.ENCRYPT_MODE);
        decryptCipher = createCipher(Cipher.DECRYPT_MODE);
    }

    public SecureEndpoint(int port) {
        super(port);
        key = generateKey();
        encryptCipher = createCipher(Cipher.ENCRYPT_MODE);
        decryptCipher = createCipher(Cipher.DECRYPT_MODE);
    }

    private Key generateKey() {
        return new SecretKeySpec(KEY_STRING.getBytes(), ALGORITHM);
    }

    private Cipher createCipher(int mode) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(mode, key);
            return cipher;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void send(InetSocketAddress receiver, Serializable payload) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(payload);
            byte[] serializedPayload = byteArrayOutputStream.toByteArray();
            byte[] encryptedPayload = encryptCipher.doFinal(serializedPayload);
            super.send(receiver, encryptedPayload);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Serializable decryptPayload(byte[] encryptedPayload) {
        try {
            byte[] decryptedPayload = decryptCipher.doFinal(encryptedPayload);
            ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(decryptedPayload));
            return (Serializable) objectInputStream.readObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Message blockingReceive() {
        Message receivedMessage = super.blockingReceive();
        Serializable decryptedPayload = decryptPayload((byte[]) receivedMessage.getPayload());
        return new Message(decryptedPayload, receivedMessage.getSender());
    }

    @Override
    public Message nonBlockingReceive() {
        Message receivedMessage = super.nonBlockingReceive();

        if (receivedMessage == null) {
            return null;
        }

        Serializable decryptedPayload = decryptPayload((byte[]) receivedMessage.getPayload());
        return new Message(decryptedPayload, receivedMessage.getSender());
    }
}

