import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

// Mirrors Crypto.kt exactly: nonce(12) || ciphertext || tag(16), AES-256-GCM.
public class Decrypt {
    public static void main(String[] a) throws Exception {
        byte[] key = Base64.getUrlDecoder().decode(a[0]);
        byte[] packet = Base64.getUrlDecoder().decode(a[1]);

        byte[] nonce = java.util.Arrays.copyOfRange(packet, 0, 12);
        byte[] sealed = java.util.Arrays.copyOfRange(packet, 12, packet.length);

        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        System.out.println("decrypted: " + new String(c.doFinal(sealed), "UTF-8"));

        // Tampering must be rejected, not silently mis-decrypted.
        sealed[0] ^= 0x01;
        try {
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            c.doFinal(sealed);
            System.out.println("PROBLEM: forged message was accepted");
        } catch (Exception e) {
            System.out.println("forged message rejected: " + e.getClass().getSimpleName());
        }
    }
}
