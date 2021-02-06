package com.topjohnwu.superuser;

import org.junit.Test;

import static org.junit.Assert.*;

public class ShellUtilsTest {

    @Test
    public void escapedString() {
        // Check default use case
        assertEquals("\"script.sh\"", ShellUtils.escapedString("script.sh"));
        // Check empty string
        assertEquals("\"\"", ShellUtils.escapedString(""));
        // Check variable substitution
        assertEquals("\"script\\${var}.sh\"", ShellUtils.escapedString("script${var}.sh"));
        // Check command substitution
        assertEquals("\"script\\`command\\`.sh\"", ShellUtils.escapedString("script`command`.sh"));
        // Check double quote escape
        assertEquals("\"script\\\".sh\"", ShellUtils.escapedString("script\".sh"));
        assertEquals("\"script.sh\\\\\"", ShellUtils.escapedString("script.sh\\"));
        // Check spaces
        assertEquals("\"script\\ with\\ spaces.sh\"", ShellUtils.escapedString("script with spaces.sh"));
        // Check command chain
        assertEquals("\"script.sh\\;command\"", ShellUtils.escapedString("script.sh;command"));
        assertEquals("\"script.sh\\&\\&command\"", ShellUtils.escapedString("script.sh&&command"));
        assertEquals("\"script.sh\\|\\|command\"", ShellUtils.escapedString("script.sh||command"));
    }
}