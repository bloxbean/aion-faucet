package com.bloxbean.contracts;

import avm.Blockchain;
import org.aion.avm.userlib.AionList;
import org.aion.avm.userlib.AionMap;

import java.util.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.security.NoSuchAlgorithmException;

/**
 * Class for generation and parsing of <a href="http://www.hashcash.org/">HashCash</a><br>
 * Copyright 2006 Gregory Rubin <a href="mailto:grrubin@gmail.com">grrubin@gmail.com</a><br>
 *  Permission is given to use, modify, and or distribute this code so long as this message remains attached<br>
 * Please see the spec at: <a href="http://www.hashcash.org/">http://www.hashcash.org/</a>
 * @author grrubin@gmail.com
 * @version 1.1
 */
public class HashCash {
    public static final int DefaultVersion = 1;
    private static final int hashLength = 160;
    private static final String dateFormatString = "yyMMdd";
    private static long milliFor16 = -1;

    private String myToken;
    private int myValue;
//    private Calendar myDate;
    private Map<String, List<String> > myExtensions;
    private int myVersion;
    private String myResource;
    // Constructors

    /**
     * Parses and validates a HashCash.
     * @throws NoSuchAlgorithmException If SHA1 is not a supported Message Digest
     */
    public HashCash(String cash) throws NoSuchAlgorithmException {
        myToken = cash;

        String[] parts = StringUtil.splits(cash,':');
        myVersion = Integer.parseInt(parts[0]);
        if(myVersion < 0 || myVersion > 1)
            throw new IllegalArgumentException("Only supported versions are 0 and 1");

        if((myVersion == 0 && parts.length != 6) ||
                (myVersion == 1 && parts.length != 7))
            throw new IllegalArgumentException("Improperly formed HashCash");

        try {
            int index = 1;
            if(myVersion == 1)
                myValue = Integer.parseInt(parts[index++]);
            else
                myValue = 0;

//            SimpleDateFormat dateFormat = new SimpleDateFormat(dateFormatString);
//            Calendar tempCal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
//            tempCal.setTime(dateFormat.parse(parts[index++]));

            index++; //Ignore date. Just go to next token

            myResource = parts[index++];
            myExtensions = deserializeExtensions(parts[index++]);

           // MessageDigest md = MessageDigest.getInstance("SHA1");
           // md.update(cash.getBytes());
           // byte[] tempBytes = md.digest();

            byte[] tempBytes = Blockchain.sha256(cash.getBytes());
            int tempValue = numberOfLeadingZeros(tempBytes);

            if(myVersion == 0)
                myValue = tempValue;
            else if (myVersion == 1)
                myValue = (tempValue > myValue ? myValue : tempValue);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Improperly formed HashCash", ex);
        }
    }
//
//    private HashCash() throws NoSuchAlgorithmException {
//    }
//
//    /**
//     * Mints a version 1 HashCash using now as the date
//     * @param resource the string to be encoded in the HashCash
//     * @throws NoSuchAlgorithmException If SHA1 is not a supported Message Digest
//     */
//    public static HashCash mintCash(String resource, int value) throws NoSuchAlgorithmException {
//        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
//        return mintCash(resource, null, now, value, DefaultVersion);
//    }
//
//    /**
//     * Mints a  HashCash  using now as the date
//     * @param resource the string to be encoded in the HashCash
//     * @param version Which version to mint.  Only valid values are 0 and 1
//     * @throws NoSuchAlgorithmException If SHA1 is not a supported Message Digest
//     */
//    public static HashCash mintCash(String resource, int value, int version) throws NoSuchAlgorithmException {
//        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
//        return mintCash(resource, null, now, value, version);
//    }
//
//    /**
//     * Mints a version 1 HashCash
//     * @param resource the string to be encoded in the HashCash
//     * @throws NoSuchAlgorithmException If SHA1 is not a supported Message Digest
//     */
//    public static HashCash mintCash(String resource, Calendar date, int value) throws NoSuchAlgorithmException {
//        return mintCash(resource, null, date, value, DefaultVersion);
//    }
//
//    /**
//     * Mints a  HashCash
//     * @param resource the string to be encoded in the HashCash
//     * @param version Which version to mint.  Only valid values are 0 and 1
//     * @throws NoSuchAlgorithmException If SHA1 is not a supported Message Digest
//     */
//    public static HashCash mintCash(String resource, Calendar date, int value, int version)
//            throws NoSuchAlgorithmException {
//        return mintCash(resource, null, date, value, version);
//    }
//
//    /**
//     * Mints a version 1 HashCash using now as the date
//     * @param resource the string to be encoded in the HashCash
//     * @param extensions Extra data to be encoded in the HashCash
//     * @throws NoSuchAlgorithmException If SHA1 is not a supported Message Digest
//     */
//    public static HashCash mintCash(String resource, Map<String, List<String> > extensions, int value)
//            throws NoSuchAlgorithmException {
//        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
//        return mintCash(resource, extensions, now, value, DefaultVersion);
//    }
//
//    /**
//     * Mints a  HashCash using now as the date
//     * @param resource the string to be encoded in the HashCash
//     * @param extensions Extra data to be encoded in the HashCash
//     * @param version Which version to mint.  Only valid values are 0 and 1
//     * @throws NoSuchAlgorithmException If SHA1 is not a supported Message Digest
//     */
//    public static HashCash mintCash(String resource, Map<String, List<String> > extensions, int value, int version)
//            throws NoSuchAlgorithmException {
//        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
//        return mintCash(resource, extensions, now, value, version);
//    }
//
//    /**
//     * Mints a version 1 HashCash
//     * @param resource the string to be encoded in the HashCash
//     * @param extensions Extra data to be encoded in the HashCash
//     * @throws NoSuchAlgorithmException If SHA1 is not a supported Message Digest
//     */
//    public static HashCash mintCash(String resource, Map<String, List<String> > extensions, Calendar date, int value)
//            throws NoSuchAlgorithmException {
//        return mintCash(resource, extensions, date, value, DefaultVersion);
//    }
//
//    /**
//     * Mints a  HashCash
//     * @param resource the string to be encoded in the HashCash
//     * @param extensions Extra data to be encoded in the HashCash
//     * @param version Which version to mint.  Only valid values are 0 and 1
//     * @throws NoSuchAlgorithmException If SHA1 is not a supported Message Digest
//     */
//    public static HashCash mintCash(String resource, Map<String, List<String> > extensions, Calendar date, int value, int version)
//            throws NoSuchAlgorithmException {
//        if(version < 0 || version > 1)
//            throw new IllegalArgumentException("Only supported versions are 0 and 1");
//
//        if(value < 0 || value > hashLength)
//            throw new IllegalArgumentException("Value must be between 0 and " + hashLength);
//
//        if(resource.contains(":"))
//            throw new IllegalArgumentException("Resource may not contain a colon.");
//
//        HashCash result = new HashCash();
//
//        MessageDigest md = MessageDigest.getInstance("SHA1");
//
//        result.myResource = resource;
//        result.myExtensions = (null == extensions ? new HashMap<String, List<String> >() : extensions);
//        result.myDate = date;
//        result.myVersion = version;
//
//        String prefix;
//
//        SimpleDateFormat dateFormat = new SimpleDateFormat(dateFormatString);
//        switch(version) {
//            case 0:
//                prefix = version + ":" + dateFormat.format(date.getTime()) + ":" + resource + ":" +
//                        serializeExtensions(extensions) + ":";
//                result.myToken = generateCash(prefix, value, md);
//                md.reset();
//                md.update(result.myToken.getBytes());
//                result.myValue = numberOfLeadingZeros(md.digest());
//                break;
//
//            case 1:
//                result.myValue = value;
//                prefix = version + ":" + value + ":" + dateFormat.format(date.getTime()) + ":" + resource + ":" +
//                        serializeExtensions(extensions) + ":";
//                result.myToken = generateCash(prefix, value, md);
//                break;
//
//            default:
//                throw new IllegalArgumentException("Only supported versions are 0 and 1");
//        }
//
//        return result;
//    }
//
    // Accessors
    /**
     * Two objects are considered equal if they are both of type HashCash and have an identical string representation
     */
    public boolean equals(Object obj) {
        if(obj instanceof HashCash)
            return toString().equals(obj.toString());
        else
            return super.equals(obj);
    }

    /**
     * Returns the canonical string representation of the HashCash
     */
    public String toString() {
        return myToken;
    }

    /**
     * Extra data encoded in the HashCash
     */
    public Map<String, List<String> > getExtensions() {
        return myExtensions;
    }

    /**
     * The primary resource being protected
     */
    public String getResource() {
        return myResource;
    }

    /**
     * The minting date
     */
    public Calendar getDate() {
        return null;// myDate;
    }

    /**
     * The value of the HashCash (e.g. how many leading zero bits it has)
     */
    public int getValue() {
        return myValue;
    }

    /**
     * Which version of HashCash is used here
     */
    public int getVersion() {
        return myVersion;
    }

//
//
//    /**
//     * Converts a 4 byte array of unsigned bytes to an long
//     * @param b an array of 4 unsigned bytes
//     * @return a long representing the unsigned int
//     */
//    private static long unsignedIntToLong(byte[] b) {
//        long l = 0;
//        l |= b[0] & 0xFF;
//        l <<= 8;
//        l |= b[1] & 0xFF;
//        l <<= 8;
//        l |= b[2] & 0xFF;
//        l <<= 8;
//        l |= b[3] & 0xFF;
//        return l;
//    }
//
//    /**
//     * Serializes the extensions with (key, value) seperated by semi-colons and values seperated by commas
//     */
//    private static String serializeExtensions(Map<String, List<String> > extensions) {
//        if(null == extensions || extensions.isEmpty())
//            return "";
//
//        StringBuffer result = new StringBuffer();
//        List<String> tempList;
//        boolean first = true;
//
//        for(String key: extensions.keySet()) {
//            if(key.contains(":") || key.contains(";") || key.contains("="))
//                throw new IllegalArgumentException("Extension key contains an illegal character. " + key);
//            if(!first)
//                result.append(";");
//            first = false;
//            result.append(key);
//            tempList = extensions.get(key);
//
//            if(null != tempList) {
//                result.append("=");
//                for(int i = 0; i < tempList.size(); i++) {
//                    if(tempList.get(i).contains(":") || tempList.get(i).contains(";") || tempList.get(i).contains(","))
//                        throw new IllegalArgumentException("Extension value contains an illegal character. " + tempList.get(i));
//                    if(i > 0)
//                        result.append(",");
//                    result.append(tempList.get(i));
//                }
//            }
//        }
//        return result.toString();
//    }
//
//    /**
//     * Inverse of {@link #serializeExtensions(Map)}
//     */
    private static Map<String, List<String> > deserializeExtensions(String extensions) {
        Map<String, List<String> > result = new AionMap<>();
        if(null == extensions || extensions.length() == 0)
            return result;

        String[] items = StringUtil.splits(extensions, ':');

        for(int i = 0; i < items.length; i++) {
            String[] parts = StringUtil.splits(items[i], ':');//items[i].split("=", 2);
            if(parts.length == 1)
                result.put(parts[0], null);
            else {
                String[] tokens = StringUtil.splits(parts[1], ':');

                List<String> values = new AionList<>();
                if(tokens != null) {
                    for (String token : tokens) {
                        values.add(token);
                    }
                }
                result.put(parts[0], values);
               // result.put(parts[0], Arrays.asList(StringUtil.splits(parts[1], ':'));//.split(",")));
            }
        }

        return result;
    }
//
    /**
     * Counts the number of leading zeros in a byte array.
     */
    private static int numberOfLeadingZeros(byte[] values) {
        int result = 0;
        int temp = 0;
        for(int i = 0; i < values.length; i++) {

            temp = numberOfLeadingZeros(values[i]);

            result += temp;
            if(temp != 8)
                break;
        }

        return result;
    }

    /**
     * Returns the number of leading zeros in a bytes binary represenation
     */
    private static int numberOfLeadingZeros(byte value) {
        if(value < 0)
            return 0;
        if(value < 1)
            return 8;
        else if (value < 2)
            return  7;
        else if (value < 4)
            return 6;
        else if (value < 8)
            return 5;
        else if (value < 16)
            return 4;
        else if (value < 32)
            return 3;
        else if (value < 64)
            return 2;
        else if (value < 128)
            return 1;
        else
            return 0;
    }
//
//
//
//    /**
//     * Compares the value of two HashCashes
//     * @param other
//     * @see java.lang.Comparable#compareTo(Object)
//     */
//    public int compareTo(HashCash other) {
//        if(null == other)
//            throw new NullPointerException();
//
//        return Integer.valueOf(getValue()).compareTo(Integer.valueOf(other.getValue()));
//    }
}
