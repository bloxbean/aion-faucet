package com.bloxbean.contracts;

import org.aion.avm.userlib.AionList;

import java.util.List;

public class StringUtil {

    public static String[] splits(String str, char delimeter) {
        List<String> tokens = new AionList<>();
        int i = str.indexOf(delimeter);

        if(i == -1)
            return new String[]{str};

        while (i != -1) {
            String token = str.substring(0, i);
            tokens.add(token);

            if(str.length() > i + 1) {
                str = str.substring(i + 1);

                i = str.indexOf(delimeter);

                if(i == -1) {
                    tokens.add(str);
                }
            } else {
                i = -1;
            }
        }

        int length = tokens.size();
        String[] retList = new String[length];

        i = 0;
        for(String token: tokens) {
            retList[i++] = token;
        }
        return retList;
    }
}
