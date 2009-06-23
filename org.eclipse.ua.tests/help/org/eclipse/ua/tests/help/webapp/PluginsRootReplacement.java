/*******************************************************************************
 *  Copyright (c) 2009 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.ua.tests.help.webapp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import junit.framework.TestCase;

import org.eclipse.help.internal.webapp.servlet.PluginsRootResolvingStream;

/**
 * Test for replacing PLUGINS_ROOT with a relative path
 */

public class PluginsRootReplacement extends TestCase {

	public void testEmpty() {
	    final String input = "";
		checkFilter(input, input);
	}

	public void testNoMatch() {
	    final String input = "<HEAD><HEAD/>";
		checkFilter(input, input);
	}

	public void testPartialMatch() {
	    final String input = "<A href = \"PLUGINS\">";
		checkFilter(input, input);
	}
	
	public void testEndsUnmatched() {
	    final String input = "<A href = \"PLUGIN";
		checkFilter(input, input);
	}

	public void testNotAtStart() {
	    final String input = "<A href = \"../PLUGINS_ROOT/plugin/a.html\">";
		checkFilter(input, input);
	}

	public void testAtStart() {
	    final String input = "<A href = \"PLUGINS_ROOT/plugin/a.html\">";
	    final String expected = "<A href = \"../plugin/a.html\">";
		checkFilter(input, expected);
	}

	public void testSecondArg() {
	    final String input = "<A alt=\"alt\" href = \"PLUGINS_ROOT/plugin/a.html\">";
	    final String expected = "<A alt=\"alt\" href = \"../plugin/a.html\">";
		checkFilter(input, expected);
	}
	

	public void testMultipleMatches() {
	    final String input = "<A href = \"PLUGINS_ROOT/plugin/a.html\"><A href = \"PLUGINS_ROOT/plugin/b.html\">";
	    final String expected = "<A href = \"../plugin/a.html\"><A href = \"../plugin/b.html\">";
		checkFilter(input, expected);
	}

	private void checkFilter(final String input, final String expected) {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		OutputStream filteredOutput = new PluginsRootResolvingStream(output, "../");
		try {
			filteredOutput.write(input.getBytes());
			filteredOutput.close();
		} catch (IOException e) {
			fail("IO Exception");
		}
		assertEquals(expected, output.toString());
	}
	
}