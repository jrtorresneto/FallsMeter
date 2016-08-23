package com.example.jtorres.fallsmeter.utils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class Comunicator {

	public static final int BUFFER_SIZE = 1024;

	public static void sendString(DataOutputStream writer, String message) throws Exception {
		int length = message.length();
		writer.writeInt(length);
		byte[] buffer = message.getBytes("UTF-8");
		writer.write(buffer);
	}

	public static String recvString(DataInputStream reader) throws Exception {
		int length = reader.readInt();
		byte[] buffer = new byte[length];
		reader.read(buffer);
		return new String(buffer, "UTF-8");
	}

	public static void sendFile(DataOutputStream writer, File file) throws Exception {
		FileInputStream fis = new FileInputStream(file);
		byte[] mybytearray = new byte[(int)file.length()];
		writer.writeInt((int)file.length());
		sendString(writer, file.getName());
		System.out.println(fis.read(mybytearray,0,mybytearray.length));
        writer.write(mybytearray,0,mybytearray.length);
        writer.flush();
		System.out.println("END");
	}

	public static String recvFile(DataInputStream reader, String path) throws Exception {
		int length = reader.readInt();
		String fileName = recvString(reader);
		byte[] buffer = new byte[length];
		System.out.println(length);
		int bytesRead;
		int current = 0;
		bytesRead = reader.read(buffer, 0, buffer.length);
		current = bytesRead;

		do {
			bytesRead = reader.read(buffer, current, (buffer.length - current));
			if (bytesRead >= 0) current += bytesRead;

		} while(current < length);//bytesRead != -1);
		System.out.println(current);

		String photopath = path + fileName;
		File file = new File(photopath);
		file.createNewFile();
	
		FileOutputStream fos = new FileOutputStream(file);
		fos.write(buffer, 0, current);
		fos.flush();

		return photopath;
	}

}
