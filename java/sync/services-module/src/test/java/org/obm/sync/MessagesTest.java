/* ***** BEGIN LICENSE BLOCK *****
 * 
 * Copyright (C) 2011-2012  Linagora
 *
 * This program is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU Affero General Public License as 
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version, provided you comply 
 * with the Additional Terms applicable for OBM connector by Linagora 
 * pursuant to Section 7 of the GNU Affero General Public License, 
 * subsections (b), (c), and (e), pursuant to which you must notably (i) retain 
 * the “Message sent thanks to OBM, Free Communication by Linagora” 
 * signature notice appended to any and all outbound messages 
 * (notably e-mail and meeting requests), (ii) retain all hypertext links between 
 * OBM and obm.org, as well as between Linagora and linagora.com, and (iii) refrain 
 * from infringing Linagora intellectual property rights over its trademarks 
 * and commercial brands. Other Additional Terms apply, 
 * see <http://www.linagora.com/licenses/> for more details. 
 *
 * This program is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License 
 * for more details. 
 *
 * You should have received a copy of the GNU Affero General Public License 
 * and its applicable Additional Terms for OBM along with this program. If not, 
 * see <http://www.gnu.org/licenses/> for the GNU Affero General Public License version 3 
 * and <http://www.linagora.com/licenses/> for the Additional Terms applicable to 
 * OBM connectors. 
 * 
 * ***** END LICENSE BLOCK ***** */
package org.obm.sync;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createControl;
import static org.easymock.EasyMock.expect;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.easymock.IMocksControl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.obm.configuration.ConfigurationServiceImpl;
import org.obm.configuration.utils.IniFile;
import org.obm.configuration.utils.IniFile.Factory;
import org.obm.filter.SlowFilterRunner;

import com.google.common.collect.ImmutableMap;

@RunWith(SlowFilterRunner.class)
public class MessagesTest {

	private ConfigurationServiceImpl configurationService;

	@Before
	public void setLocale() {
		IMocksControl control = createControl();
		IniFile iniFile = control.createMock(IniFile.class);
		expect(iniFile.getData()).andReturn(ImmutableMap.<String, String>of());
		Factory factory = control.createMock(IniFile.Factory.class);
		expect(factory.build(anyObject(String.class))).andReturn(iniFile);
		control.replay();
		configurationService = new ConfigurationServiceImpl.Factory().create("fakeConfPath", "appName");
		Locale.setDefault(Locale.US);
	}

	@Test
	public void testResourceBundleFr() {
		Messages messages = new Messages(configurationService, Locale.FRENCH);
		String expectedMessage = "Nouvel événement de owner : title";
		String message = messages.newEventTitle("owner", "title");
		assertThat(message).isEqualTo(expectedMessage);
	}
	
	@Test
	public void testResourceBundleEn() {
		Messages messages = new Messages(configurationService, Locale.ENGLISH);
		String expectedMessage = "New event from owner: title";
		String message = messages.newEventTitle("owner", "title");
		assertThat(message).isEqualTo(expectedMessage);
	}
	
	@Test
	public void testResourceBundleZh() {
		Messages messages = new Messages(configurationService, Locale.CHINESE);
		String expectedMessage = "新的事件从 owner: title";
		String message = messages.newEventTitle("owner", "title");
		assertThat(message).isEqualTo(expectedMessage);
	}
	
	@Test
	public void testResourceBundleItNotExist() {
		Messages messages = new Messages(configurationService, Locale.ITALIAN);
		String expectedMessage = "New event from owner: title";
		String message = messages.newEventTitle("owner", "title");
		assertThat(message).isEqualTo(expectedMessage);
	}
}
