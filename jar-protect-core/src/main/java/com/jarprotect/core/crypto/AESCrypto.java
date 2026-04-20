package com.jarprotect.core.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;

/**
 * AES-256-GCM 加密解密工具。
 * 数据格式: [salt(16字节)] [nonce(12字节)] [密文+GCM认证标签]
 */
public class AESCrypto {

    private static final String KEY_ALGORITHM = "AES";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";

    private static final int SALT_LENGTH = 16;
    private static final int NONCE_LENGTH = 12;
    private static final int KEY_LENGTH = 256;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int PBKDF2_ITERATIONS = 65536;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 使用密码对数据进行 AES-256-GCM 加密。
     *
     * @param plainData 明文数据
     * @param password  加密密码
     * @return 加密后的数据 [salt(16)] [nonce(12)] [ciphertext+tag]
     */
    public static byte[] encrypt(byte[] plainData, String password) throws Exception {
        byte[] salt = new byte[SALT_LENGTH];
        SECURE_RANDOM.nextBytes(salt);

        byte[] nonce = new byte[NONCE_LENGTH];
        SECURE_RANDOM.nextBytes(nonce);

        SecretKey key = deriveKey(password, salt);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);

        byte[] cipherText = cipher.doFinal(plainData);

        // 拼接: salt + nonce + cipherText
        byte[] result = new byte[SALT_LENGTH + NONCE_LENGTH + cipherText.length];
        System.arraycopy(salt, 0, result, 0, SALT_LENGTH);
        System.arraycopy(nonce, 0, result, SALT_LENGTH, NONCE_LENGTH);
        System.arraycopy(cipherText, 0, result, SALT_LENGTH + NONCE_LENGTH, cipherText.length);

        return result;
    }

    /**
     * 使用密码对数据进行 AES-256-GCM 解密。
     *
     * @param encryptedData 加密数据 [salt(16)] [nonce(12)] [ciphertext+tag]
     * @param password      解密密码
     * @return 明文数据
     */
    public static byte[] decrypt(byte[] encryptedData, String password) throws Exception {
        if (encryptedData.length < SALT_LENGTH + NONCE_LENGTH + GCM_TAG_LENGTH / 8) {
            throw new IllegalArgumentException("加密数据格式不正确：长度不足");
        }

        byte[] salt = Arrays.copyOfRange(encryptedData, 0, SALT_LENGTH);
        byte[] nonce = Arrays.copyOfRange(encryptedData, SALT_LENGTH, SALT_LENGTH + NONCE_LENGTH);
        byte[] cipherText = Arrays.copyOfRange(encryptedData, SALT_LENGTH + NONCE_LENGTH, encryptedData.length);

        SecretKey key = deriveKey(password, salt);

        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

        return cipher.doFinal(cipherText);
    }

    /**
     * 使用 PBKDF2 从密码派生 AES-256 密钥。
     */
    private static SecretKey deriveKey(String password, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, KEY_ALGORITHM);
    }
}
