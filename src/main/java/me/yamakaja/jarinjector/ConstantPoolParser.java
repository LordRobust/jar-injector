package me.yamakaja.jarinjector;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * Created by Yamakaja on 06.06.17.
 */
public class ConstantPoolParser {

    public static final int CLASS = 7;
    public static final int STR = 8;
    public static final int MTYPE = 16;
    private static final int FIELD = 9;
    private static final int METH = 10;
    private static final int IMETH = 11;
    private static final int INT = 3;
    private static final int FLOAT = 4;
    private static final int LONG = 5;
    private static final int DOUBLE = 6;
    private static final int NAME_TYPE = 12;
    private static final int UTF8 = 1;
    private static final int HANDLE = 15;
    private static final int INDY = 18;
    public static LongAdder timeCounter = new LongAdder();
    private static Charset utf8 = Charset.forName("UTF-8");

    public ConstantPoolParser(String className, DataInputStream inputStream, DataOutputStream outputStream, Map<String, String> stringReplacements) {
        long startTime = 0;
        if (Bootstrap.debug)
            startTime = System.nanoTime();

        try {
            int magic = inputStream.readInt();

            if (magic != -889275714)
                throw new RuntimeException("Class " + className + " starts with unknown magic number!");

            outputStream.writeInt(magic);

            skip(inputStream, outputStream, 4);

            int length = inputStream.readUnsignedShort();
            outputStream.writeShort(length);

            for (int i = 1; i < length; ++i) {
                int toSkip;

                int tag = inputStream.readUnsignedByte();
                outputStream.writeByte(tag);
                switch (tag) {
                    case FIELD:
                    case METH:
                    case IMETH:
                    case INT:
                    case FLOAT:
                    case NAME_TYPE:
                    case INDY:
                        toSkip = 4;
                        break;
                    case LONG:
                    case DOUBLE:
                        toSkip = 8;
                        ++i;
                        break;
                    case UTF8:
                        handleString(inputStream, outputStream, inputStream.readUnsignedShort(), stringReplacements);
                        toSkip = 0;
                        break;
                    case HANDLE:
                        toSkip = 3;
                        break;
                    default:
                        toSkip = 2;
                        break;
                }

                skip(inputStream, outputStream, toSkip);
            }
            int accessFlags = inputStream.readUnsignedShort();
            outputStream.writeShort(accessFlags);

            if ((accessFlags & (0x0001 | 0x0010 | 0x0020 | 0x0200 | 0x0400 | 0x1000 | 0x2000 | 0x4000)) != accessFlags)
                throw new RuntimeException("Invalid bytes in header of " + className + "!");

            int count;
            byte[] buffer = new byte[1024];
            while ((count = inputStream.read(buffer)) > 0)
                outputStream.write(buffer, 0, count);

            inputStream.close();

        } catch (IOException e) {
            System.err.println("An error occurred while parsing " + className);
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        if (Bootstrap.debug)
            timeCounter.add(System.nanoTime() - startTime);
    }

    private static void skip(InputStream input, OutputStream output, int amount) throws IOException {
        byte[] buffer = new byte[Math.min(amount, 1024)];
        int count;
        while (amount > 0) {
            count = input.read(buffer, 0, amount);
            output.write(buffer, 0, count);
            amount -= count;
        }
    }

    private static void handleString(InputStream input, DataOutputStream output, int size, Map<String, String> replacements) throws IOException {
        byte[] buffer = new byte[size];

        int read = 0;
        while (read != size)
            read += input.read(buffer, read, size - read);

        final String[] str = {new String(buffer, utf8), ""};

        final boolean[] changed = {false};

        replacements.forEach((key, value) -> {
            str[1] = str[0].replace(key, value);
            if (!str[0].equals(str[1]))
                changed[0] = true;
            str[0] = str[1];
        });

        if (changed[0])
            buffer = str[0].getBytes(utf8);

        output.writeShort(buffer.length);
        output.write(buffer, 0, buffer.length);
    }

}
