package encryption;

import java.awt.HeadlessException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import settings.Settings;

public class SyncropCipher {
	private static final String ENCRYPTION_ENDING=".SYNCROP_ENCRYPTED_FILE";
	public static void decrypt(File encryptedFile,String keyString){
		crypt(encryptedFile,new File(encryptedFile.getParent(),getDecryptedName(encryptedFile.getName())), keyString, false);
	}
	public static void decrypt(File encryptedFile,File outputFile,String keyString){
		crypt(encryptedFile, outputFile, keyString, false);
	}
	public static void encrypt(File unencryptedFile,String keyString){
		crypt(unencryptedFile,new File(unencryptedFile.getParent(),getEncryptedName(unencryptedFile.getName())), keyString, true);
	}
	public static void encrypt(File uncryptedFile,File outputFile,String keyString){
		crypt(uncryptedFile, outputFile, keyString, true);
	}
	private static void crypt(File in,File out,String keyString,boolean encrypt){

		try {
			
			Key key = new SecretKeySpec(keyString.getBytes(), Settings.getEncryptionAlgorithm());

			Cipher c = Cipher.getInstance(Settings.getEncryptionAlgorithm());
			c.init(encrypt?Cipher.ENCRYPT_MODE:Cipher.DECRYPT_MODE, key);
			
			
			CipherOutputStream cos = new CipherOutputStream(
					new FileOutputStream(out),
					c);
			
			Files.copy(in.toPath(), cos);
			cos.close();
		} catch (HeadlessException | InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException
				| IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
	}
	private static String getEncryptedName(String name){
		return name+ENCRYPTION_ENDING+"."+Settings.getEncryptionAlgorithm();
	}
	private static String getDecryptedName(String name){
		return name.substring(0,name.lastIndexOf(ENCRYPTION_ENDING));
	}
	public static boolean isEncrypted(String name){
		System.out.println(name);
		System.out.println(name.matches(".+"+ENCRYPTION_ENDING+"\\..*"));
		System.out.println(name.matches(ENCRYPTION_ENDING));
		return name.matches(".+"+ENCRYPTION_ENDING+"\\..*");
	}
}
