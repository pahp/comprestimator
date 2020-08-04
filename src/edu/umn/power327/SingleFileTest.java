package edu.umn.power327;

import edu.umn.power327.database.DBAdapter;

import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.Scanner;
import java.util.zip.Deflater;

class SingleFileTest {

    public static void main(String[] args) throws Exception {
        DBAdapter dbAdapter = new DBAdapter();
        dbAdapter.createTables();
        FileSystem fs = FileSystems.getDefault();
        Path path = null;
        if (args.length != 0) {
            try {
                path = fs.getPath(args[1]);
            } catch (InvalidPathException ignored) { } // gulp
        }

        if (path == null) {
            Scanner scanner = new Scanner(System.in);
            System.out.println("Enter the path to some file: ");
            String pathString = scanner.nextLine();
            try {
                path = fs.getPath(pathString);
            } catch (InvalidPathException e) {
                e.printStackTrace();
                System.out.println("Bad path. Exiting.");
                return;
            }
        }
        Deflater compressor = new Deflater();
        String hash;
        byte[] input, output = new byte[1073741824];
        long start, stop;
        int compressSize;
        // do compression test here
        input = Files.readAllBytes(path);
//        output = new byte[input.length + 1];
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        hash = stringify(md.digest(input));
        System.out.println(hash);
        // 2) compressor.setInput(inputByte[]);
        compressor.setInput(input);
        compressor.finish();
        // 3) TIMER START
        start = System.currentTimeMillis();
        // 4) compressor.deflate(outputByte[]);
        compressSize = compressor.deflate(output);
        // 5) TIMER STOP
        stop = System.currentTimeMillis();

        // try DB stuff here
        try {
            dbAdapter.insertResult("deflate_results", hash,
                    getExt(path), input.length / 1000.0, compressSize / 1000.0, (int)(stop - start) / 1000);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static String stringify(byte[] digest) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : digest) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }

        return hexString.toString();
    }

    public static String getExt(Path path) {
        String s = path.toString();
        if(s.matches("\\.[^\\./\\\\]+$")) {
            return s.substring(s.lastIndexOf(".") + 1);
        }
        else return "";
    }
}