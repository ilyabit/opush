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
package org.obm.push.mail.imap;

import java.util.Properties;
import java.util.Set;

import javax.mail.NoSuchProviderException;
import javax.mail.Session;

import org.minig.imap.IdleClient;
import org.minig.imap.StoreClient;
import org.obm.configuration.EmailConfiguration;
import org.obm.locator.LocatorClientException;
import org.obm.locator.store.LocatorService;
import org.obm.push.bean.UserDataRequest;
import org.obm.push.exception.NoImapClientAvailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.sun.mail.imap.IMAPStore;

public class ImapClientProviderImpl implements ImapClientProvider {

	private static final Set<String> AVAILABLE_PROTOCOLS = ImmutableSet.of("imap", "imaps");

	private static final Logger logger = LoggerFactory.getLogger(ImapClientProviderImpl.class);

	
	private final LocatorService locatorService;
	private final ManagedLifecycleImapStore.Factory managedLifecycleImapStoreFactory;
	private final boolean loginWithDomain;
	private final int imapPort;
	@VisibleForTesting final Session defaultSession;

	@Inject
	@VisibleForTesting ImapClientProviderImpl(EmailConfiguration emailConfiguration, LocatorService locatorService,
			ManagedLifecycleImapStore.Factory managedLifecycleImapStoreFactory) {
		this.locatorService = locatorService;
		this.managedLifecycleImapStoreFactory = managedLifecycleImapStoreFactory;
		this.loginWithDomain = emailConfiguration.loginWithDomain();
		this.imapPort = emailConfiguration.imapPort();
		
		Properties imapProperties = buildProperties(emailConfiguration);
		this.defaultSession = Session.getInstance(imapProperties);
	}

	private Properties buildProperties(EmailConfiguration emailConfiguration) {
		boolean activateTls = emailConfiguration.activateTls();
		int imapTimeout = emailConfiguration.imapTimeout();
		int imapFetchBlockSize = emailConfiguration.getImapFetchBlockSize();
		logger.debug("Java Mail settings : STARTTLS=" + activateTls);
		logger.debug("Java Mail settings : TIMEOUT=" + imapTimeout);
		logger.debug("Java Mail settings : BLOCKSIZE=" + imapFetchBlockSize);

		Properties properties = new Properties();
		properties.put("mail.debug", "false");
		properties.put("mail.imap.starttls.enable", activateTls);

		for (String availableProtocol : AVAILABLE_PROTOCOLS) {
			properties.put("mail." + availableProtocol +".timeout", imapTimeout);
			properties.put("mail." + availableProtocol +".fetchsize", imapFetchBlockSize);
		}
		
		return properties;
	}

	
	@Override
	public String locateImap(UserDataRequest udr) throws LocatorClientException {
		String imapLocation = locatorService.
				getServiceLocation("mail/imap_frontend", udr.getUser().getLoginAtDomain());
		logger.info("Using {} as imap host.", imapLocation);
		return imapLocation;
	}

	@Override
	public StoreClient getImapClient(UserDataRequest udr) throws LocatorClientException {
		final String imapHost = locateImap(udr);
		final String login = getLogin(udr);
		StoreClient storeClient = new StoreClient(imapHost, imapPort, login, udr.getPassword()); 
		
		logger.debug("Creating storeClient with login {} : " +
				"loginWithDomain = {}", 
				new Object[]{login, loginWithDomain});
		
		return storeClient; 
	}
	
	@Override
	public ImapStore getImapClientWithJM(UserDataRequest udr) throws LocatorClientException, NoImapClientAvailableException {
		final String imapHost = locateImap(udr);
		final String login = getLogin(udr);
		
		try {
			logger.debug("Creating storeClient with login {} : loginWithDomain = {}", 
					new Object[]{login, loginWithDomain});

			IMAPStore javaMailStore = (IMAPStore) defaultSession.getStore(EmailConfiguration.IMAP_PROTOCOL);
			return managedLifecycleImapStoreFactory.create(
					defaultSession, javaMailStore, login, udr.getPassword(), imapHost, imapPort);
		} catch (NoSuchProviderException e) {
			throw new NoImapClientAvailableException(
					"No client available for protocol : " + EmailConfiguration.IMAP_PROTOCOL, e);
		}
	}

	private String getLogin(UserDataRequest udr) {
		String login = udr.getUser().getLoginAtDomain();
		if (!loginWithDomain) {
			int at = login.indexOf("@");
			if (at > 0) {
				login = login.substring(0, at);
			}
		}
		return login;
	}


	@Override
	public IdleClient getImapIdleClient(UserDataRequest udr)
			throws LocatorClientException {
		String login = getLogin(udr);
		logger.debug("Creating idleClient with login: {}, (useDomain {})", login, loginWithDomain);
		return new IdleClient(locateImap(udr), 143, login, udr.getPassword());
	}

}
