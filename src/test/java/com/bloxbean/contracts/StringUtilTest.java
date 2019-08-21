package com.bloxbean.contracts;

import org.junit.Assert;
import org.junit.Test;

public class StringUtilTest {

    @Test
    public void testSplit() {

            String str = "1:20:060408:adam@xxxxxxx.org::1QTjaYd7niiQA/sc:ePa";

        String[] tokens = StringUtil.splits(str, ':');

        for(String token: tokens) {
            System.out.println(token);
        }

        Assert.assertEquals("1", tokens[0]);
        Assert.assertEquals("20", tokens[1]);
        Assert.assertEquals("060408", tokens[2]);
        Assert.assertEquals("adam@xxxxxxx.org", tokens[3]);
        Assert.assertEquals("", tokens[4]);
        Assert.assertEquals("1QTjaYd7niiQA/sc", tokens[5]);
        Assert.assertEquals("ePa", tokens[6]);

    }
}
