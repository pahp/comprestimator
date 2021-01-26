package edu.umn.power327;

import SevenZip.LzmaEncoder;
import edu.umn.power327.database.DBController;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Exception;
import net.jpountz.lz4.LZ4Factory;

import java.awt.*;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.zip.*;

public class CompressorManager {

    // compressors
    private Deflater deflater1, deflater6, deflater9;
    private LZ4Compressor lz4Compressor;
    private LZ4Compressor lz4hc;
    private LzmaEncoder lzmaEncoder;

    private final CompressionResult result = new CompressionResult();
    private ArrayList<Path> fileList;
    private byte[] input;
    private final byte[] output = new byte[1610612736]; // 1.5 GB
    private long start, stop;
    private boolean list_files = false;
    private DBController dbController;
    private Robot robot; // will be instantiated if not headless env

    public CompressorManager() throws Exception {
        new CompressorManager(true, true, true, true, true, true, false);
    }

    public CompressorManager(boolean useDeflate1, boolean useDeflate6, boolean useDeflate9, boolean useLZ4,
                             boolean useLZ4HC, boolean useLZMA, boolean list_files) throws Exception {
        this.list_files = list_files;
        if (useDeflate1) deflater1 = new Deflater(1);
        if (useDeflate6) deflater6 = new Deflater();
        if (useDeflate9) deflater9 = new Deflater(9);
        LZ4Factory lz4Factory = LZ4Factory.fastestInstance();
        if (useLZ4) lz4Compressor = lz4Factory.fastCompressor();
        if (useLZ4HC) lz4hc = lz4Factory.highCompressor();
        if (useLZMA) lzmaEncoder = new LzmaEncoder();

        if (!GraphicsEnvironment.isHeadless()) {
            robot = new Robot(); // hacky way to keep computer awake
        } else {
            System.out.println("!!! Java has no graphics access!");
            System.out.println("Please make sure your computer will not fall asleep when idle!");
            System.out.println("Check README if you need help.\n\t------------------------------");
        }

        dbController = new DBController();
        dbController.createTables();
    }

    public void beginLoop() throws Exception {

        Point mousePoint; // never instantiated when in headless env
        // using singleFileTest will be faster than multiple calls to fileList.size()
        boolean singleFileTest = fileList.size() == 1;
        FileWriter fw = null;

        if (list_files) {
            fw = new FileWriter("input_log.txt");
            System.out.println("Comprestimator will print names of compressed files"
                    + "to input_log.txt");
        }

        System.out.println("Beginning compression loop...");
        for(Path path : fileList) {
            try {
                if (fw != null) {
                    fw.write(path.toString() + "\n");
                    fw.flush();
                }
                // turn file into byte[] and get metadata
                input = Files.readAllBytes(path);
                result.setOrigSize(input.length);
                result.setHash(getHash(input));
                result.setExt(getExt(path));
                // check if we've seen this file before
                if (dbController.contains(result.getHash(), result.getOrigSize())) {
                    if (singleFileTest) {
                        dbController.deleteFromAll(result.getHash(), result.getOrigSize());
                    }
                    else
                        continue;
                }


                ///////////////////////////////////////////////////////
                // BEGIN DEFLATE
                // at level 1
                doDeflate1();

                // level 6
                doDeflate6();

                // level 9
                doDeflate9();

                // END DEFLATE ////////////////////////////////////////

                if (robot != null) {
                    mousePoint = MouseInfo.getPointerInfo().getLocation();
                    robot.mouseMove(mousePoint.x, mousePoint.y);
                }

                ///////////////////////////////////////////////////////
                // BEGIN LZ4
                try {

                    doLZ4();
                    doLZ4HC();

                } catch (LZ4Exception e) {
                    System.out.println("LZ4Exception caught: ");
                    System.out.println(path.toString());
                }
                // END LZ4 ////////////////////////////////////////////

                ///////////////////////////////////////////////////////
                // BEGIN LZMA
                doLZMA();
                // END LZMA

                if (robot != null) {
                    mousePoint = MouseInfo.getPointerInfo().getLocation();
                    robot.mouseMove(mousePoint.x, mousePoint.y);
                }
            } catch (OutOfMemoryError e) {
                System.out.println(" --- OOM Error caught:");
                System.out.println(path.toString());
                System.out.println("Continuing compression loop...");
            } catch (SQLException e) {
                e.printStackTrace();
            } catch (IOException ignored) {
                // catches AccessDenied and FileNotFound
                // continue;
            }

        } // END COMPRESSION LOOP
    }

    public static String getHash(byte[] input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(input);
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
        if(s.matches(".*\\.[A-Za-z0-9]+$")) {
            int index = s.lastIndexOf('.');
            if(index > 0 && s.charAt(index - 1) != '\\' && s.charAt(index - 1) != '/') {
                return s.substring(s.lastIndexOf(".") + 1);
            }
        }
        return "";
    }

    public void setFileList(ArrayList<Path> fileList) {
        // assumes fileList is already shuffled
        this.fileList = fileList;
    }

    public CompressionResult getResult() {
        return result;
    }

    ///////////////////////////////////////////
    // Functions for individual compressors: //
    ///////////////////////////////////////////

    private void doDeflate1() throws SQLException {
        deflater1.setInput(input);
        deflater1.finish(); // signals that no new input will enter the buffer
        int byteCount = 0;

        start = System.nanoTime(); // start timer
        while (!deflater1.finished()) {
            byteCount += deflater1.deflate(output);
        }
        stop = System.nanoTime(); // stop timer
        deflater1.reset();

        result.setCompressSize(byteCount);
        result.setCompressTime((stop - start) / 1000);

        // store deflate results in the database
        dbController.insertResult("deflate1_results", result);
    }

    private void doDeflate6() throws SQLException {
        deflater6.setInput(input);
        deflater6.finish(); // signals that no new input will enter the buffer
        int byteCount = 0;

        start = System.nanoTime(); // start timer
        while (!deflater6.finished()) {
            byteCount += deflater6.deflate(output);
        }
        stop = System.nanoTime(); // stop timer
        deflater6.reset();

        result.setCompressSize(byteCount);
        result.setCompressTime((stop - start) / 1000);

        // store deflate1 results in the database
        dbController.insertResult("deflate6_results", result);
    }

    private void doDeflate9() throws SQLException {
        deflater9.setInput(input);
        deflater9.finish(); // signals that no new input will enter the buffer
        int byteCount = 0;

        start = System.nanoTime(); // start timer
        while (!deflater9.finished()) {
            byteCount += deflater9.deflate(output);
        }
        stop = System.nanoTime(); // stop timer
        deflater9.reset();

        result.setCompressSize(byteCount);
        result.setCompressTime((stop - start) / 1000);

        // store deflate9 results in the database
        dbController.insertResult("deflate9_results",result);
    }

    private void doLZ4() throws SQLException {
        start = System.nanoTime();
        result.setCompressSize(lz4Compressor.compress(input, output));
        stop = System.nanoTime();
        result.setCompressTime((stop - start) / 1000);
        // store lz4 results
        dbController.insertResult("lz4_results", result);
    }

    private void doLZ4HC() throws SQLException {
        // LZ4HC
        start = System.nanoTime();
        result.setCompressSize(lz4hc.compress(input, output));
        stop = System.nanoTime();
        result.setCompressTime((stop - start) / 1000);
        // store lz4 results
        dbController.insertResult("lz4hc_results", result);
    }

    private void doLZMA() throws Exception {
        start = System.nanoTime();
        result.setCompressSize(lzmaEncoder.encode(input));
        stop = System.nanoTime();
        result.setCompressTime((stop - start) / 1000);
        lzmaEncoder.reset();
        // store lzma results
        dbController.insertResult("lzma_results", result);
    }
}