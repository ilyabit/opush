/* ***** BEGIN LICENSE BLOCK *****
 * Copyright (C) 2011-2013  Linagora
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version, provided you comply with the Additional Terms applicable for OBM
 * software by Linagora pursuant to Section 7 of the GNU Affero General Public
 * License, subsections (b), (c), and (e), pursuant to which you must notably (i)
 * retain the displaying by the interactive user interfaces of the “OBM, Free
 * Communication by Linagora” Logo with the “You are using the Open Source and
 * free version of OBM developed and supported by Linagora. Contribute to OBM R&D
 * by subscribing to an Enterprise offer !” infobox, (ii) retain all hypertext
 * links between OBM and obm.org, between Linagora and linagora.com, as well as
 * between the expression “Enterprise offer” and pro.obm.org, and (iii) refrain
 * from infringing Linagora intellectual property rights over its trademarks and
 * commercial brands. Other Additional Terms apply, see
 * <http://www.linagora.com/licenses/> for more details.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License and
 * its applicable Additional Terms for OBM along with this program. If not, see
 * <http://www.gnu.org/licenses/> for the GNU Affero General   Public License
 * version 3 and <http://www.linagora.com/licenses/> for the Additional Terms
 * applicable to the OBM software.
 * ***** END LICENSE BLOCK ***** */
package org.obm.provisioning.processing.impl;

import static com.jayway.restassured.RestAssured.given;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;

import java.sql.SQLException;
import java.util.Date;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.obm.cyrus.imap.admin.CyrusImapService;
import org.obm.cyrus.imap.admin.CyrusManager;
import org.obm.domain.dao.UserSystemDao;
import org.obm.guice.GuiceModule;
import org.obm.guice.SlowGuiceRunner;
import org.obm.provisioning.CommonDomainEndPointEnvTest;
import org.obm.provisioning.ProfileName;
import org.obm.provisioning.beans.Batch;
import org.obm.provisioning.beans.BatchEntityType;
import org.obm.provisioning.beans.BatchStatus;
import org.obm.provisioning.beans.HttpVerb;
import org.obm.provisioning.beans.Operation;
import org.obm.provisioning.beans.Request;
import org.obm.provisioning.dao.exceptions.BatchNotFoundException;
import org.obm.provisioning.dao.exceptions.DaoException;
import org.obm.provisioning.dao.exceptions.UserNotFoundException;
import org.obm.provisioning.ldap.client.LdapManager;
import org.obm.provisioning.ldap.client.LdapService;
import org.obm.provisioning.processing.BatchProcessor;
import org.obm.push.mail.IMAPException;
import org.obm.push.mail.bean.Acl;
import org.obm.push.utils.DateUtils;
import org.obm.satellite.client.Configuration;
import org.obm.satellite.client.Connection;
import org.obm.satellite.client.SatelliteService;
import org.obm.sync.date.DateProvider;
import org.obm.sync.host.ObmHost;
import org.obm.sync.serviceproperty.ServiceProperty;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.util.Modules;

import fr.aliacom.obm.common.domain.ObmDomain;
import fr.aliacom.obm.common.system.ObmSystemUser;
import fr.aliacom.obm.common.user.ObmUser;
import fr.aliacom.obm.common.user.UserExtId;


@RunWith(SlowGuiceRunner.class)
@GuiceModule(BatchProcessorImplTest.Env.class)
public class BatchProcessorImplTest extends CommonDomainEndPointEnvTest {

	public static class Env extends AbstractModule {

		@Override
		protected void configure() {
			install(Modules.override(new CommonDomainEndPointEnvTest.Env()).with(new AbstractModule() {

				@Override
				protected void configure() {
					bind(BatchProcessor.class).to(BatchProcessorImpl.class);
				}

			}));
		}

	}

	@Inject
	private BatchProcessor processor;
	@Inject
	private UserSystemDao userSystemDao;
	@Inject
	private DateProvider dateProvider;
	@Inject
	private SatelliteService satelliteService;
	@Inject
	private LdapService ldapService;
	@Inject
	private CyrusImapService cyrusService;

	private final Date date = DateUtils.date("2013-08-01T12:00:00");

	private final ObmSystemUser obmSatelliteUser = ObmSystemUser
			.builder()
			.id(1)
			.login("obmsatelliterequest")
			.password("secret")
			.build();
	
	private final ObmSystemUser obmCyrusUser = ObmSystemUser
			.builder()
			.id(2)
			.login("cyrus")
			.password("secret")
			.build();

	@Test
	public void testProcessWithInvalidJSONData() throws Exception {
		Operation.Builder opBuilder = Operation
				.builder()
				.id(operationId(1))
				.status(BatchStatus.IDLE)
				.entityType(BatchEntityType.USER)
				.request(org.obm.provisioning.beans.Request
						.builder()
						.url("/users/")
						.verb(HttpVerb.POST)
						.body(	"{" +
									"\"invalid\": \"json\"" +
								"}")
						.build());
		Batch.Builder batchBuilder = Batch
				.builder()
				.id(batchId(1))
				.domain(domain)
				.status(BatchStatus.IDLE)
				.operation(opBuilder.build());

		expectDomain();
		expectBatchCreationAndRetrieval(batchBuilder.build());

		expect(batchDao.update(batchBuilder
				.operation(opBuilder
						.status(BatchStatus.ERROR)
						.error("org.obm.provisioning.exception.ProcessingException: Cannot parse ObmUser object from request body {\"invalid\": \"json\"}.")
						.timecommit(date)
						.build())
				.status(BatchStatus.SUCCESS)
				.timecommit(date)
				.build())).andReturn(null);

		mocksControl.replay();

		createBatchWithOneUserAndCommit();

		mocksControl.verify();
	}

	@Test
	public void testProcessCreateUser() throws Exception {
		Date date = DateUtils.date("2013-08-01T12:00:00");
		Operation.Builder opBuilder = Operation
				.builder()
				.id(operationId(1))
				.status(BatchStatus.IDLE)
				.entityType(BatchEntityType.USER)
				.request(org.obm.provisioning.beans.Request
						.builder()
						.url("/users/")
						.verb(HttpVerb.POST)
						.body(	"{" +
										"\"id\": \"extIdUser1\"," +
										"\"login\": \"user1\"," +
										"\"profile\": \"user\"," +
										"\"password\": \"secret\"," +
										"\"mails\":[\"john@domain\"]" +
								"}")
						.build());
		Batch.Builder batchBuilder = Batch
				.builder()
				.id(batchId(1))
				.domain(domain)
				.status(BatchStatus.IDLE)
				.operation(opBuilder.build());
		
		final ObmUser user = ObmUser
				.builder()
				.login("user1")
				.password("secret")
				.emailAndAliases("john@domain")
				.profileName(ProfileName.valueOf("user"))
				.extId(UserExtId.valueOf("extIdUser1"))
				.domain(domain)
				.mailHost(ObmHost.builder().name("host").build())
				.build();

		expectDomain();
		expectBatchCreationAndRetrieval(batchBuilder.build());
		expect(userDao.create(user)).andReturn(user);
		expectLdapCreateUser(user);
		expectCyrusCreateMailbox(user);
		
		expect(batchDao.update(batchBuilder
				.operation(opBuilder
						.status(BatchStatus.SUCCESS)
						.timecommit(date)
						.build())
				.status(BatchStatus.SUCCESS)
				.timecommit(date)
				.build())).andReturn(null);
		
		mocksControl.replay();

		createBatchWithOneUserAndCommit();

		mocksControl.verify();
	}

	private void expectCyrusCreateMailbox(final ObmUser user)
			throws DaoException, IMAPException {
		CyrusManager cyrusManager = expectCyrusBuild();
		cyrusManager.create(user);
		expectLastCall().once();
		cyrusManager.setAcl(user, "anyone", Acl.builder().user("user1").rights("p").build());
		expectLastCall().once();
		expectCyrusShutDown(cyrusManager);
	}

	@Test
	public void testProcessCreateUserAndUpdateSatellite() throws Exception {
		ObmDomain domainWithMailHost = ObmDomain
				.builder()
				.from(domain)
				.host(ServiceProperty
						.builder()
						.service("mail")
						.property("smtp_in")
						.build(), ObmHost
						.builder()
						.name("Postfix")
						.ip("1.2.3.4")
						.build())
				.build();
		Operation.Builder opBuilder = Operation
				.builder()
				.id(operationId(1))
				.status(BatchStatus.IDLE)
				.entityType(BatchEntityType.USER)
				.request(org.obm.provisioning.beans.Request
						.builder()
						.url("/users/")
						.verb(HttpVerb.POST)
						.body(	"{" +
										"\"id\": \"extIdUser1\"," +
										"\"login\": \"user1\"," +
										"\"profile\": \"user\"," +
										"\"password\": \"secret\"" +
								"}")
						.build());
		Batch.Builder batchBuilder = Batch
				.builder()
				.id(batchId(1))
				.domain(domainWithMailHost)
				.status(BatchStatus.IDLE)
				.operation(opBuilder.build());
		Connection satelliteConnection = mocksControl.createMock(Connection.class);

		expectDomain();
		expectBatchCreationAndRetrieval(batchBuilder.build());

		ObmUser user = ObmUser
				.builder()
				.login("user1")
				.password("secret")
				.profileName(ProfileName.valueOf("user"))
				.extId(UserExtId.valueOf("extIdUser1"))
				.domain(domainWithMailHost)
				.build();
		expect(userDao.create(user)).andReturn(user);
		expectLdapCreateUser(user);
		expect(userSystemDao.getByLogin("obmsatelliterequest")).andReturn(obmSatelliteUser);
		expect(satelliteService.create(isA(Configuration.class), eq(domainWithMailHost))).andReturn(satelliteConnection);
		satelliteConnection.updateMTA();
		expectLastCall();
		expect(batchDao.update(batchBuilder
				.operation(opBuilder
						.status(BatchStatus.SUCCESS)
						.timecommit(date)
						.build())
				.status(BatchStatus.SUCCESS)
				.timecommit(date)
				.build())).andReturn(null);

		mocksControl.replay();

		createBatchWithOneUserAndCommit();

		mocksControl.verify();
	}

	private void createBatchWithOneUserAndCommit() {
		given()
			.auth().basic("username@domain", "password")
			.post("/batches/");
		given()
			.auth().basic("username@domain", "password")
			.post("/batches/1/users");
		given()
            .auth().basic("username@domain", "password")
            .put("/batches/1");
	}

	private void expectBatchCreationAndRetrieval(Batch batch) throws Exception {
		expectSuccessfulAuthenticationAndFullAuthorization();
		expectSuccessfulAuthenticationAndFullAuthorization();
		expectSuccessfulAuthenticationAndFullAuthorization();

		expect(batchDao.create(isA(Batch.class))).andReturn(batch);
		expect(batchDao.get(batchId(1))).andReturn(batch);
		expect(batchDao.addOperation(eq(batchId(1)), isA(Operation.class))).andReturn(batch);
		expect(batchDao.get(batchId(1))).andReturn(batch);
		expect(dateProvider.getDate()).andReturn(date).anyTimes();
	}

	private void expectLdapCreateUser(ObmUser user) {
		LdapManager ldapManager = expectLdapBuild();
		ldapManager.createUser(user);
		expectLastCall();
		ldapManager.shutdown();
		expectLastCall();
	}
	
	private void expectLdapdeleteUser(ObmUser user) {
		LdapManager ldapManager = expectLdapBuild();
		ldapManager.deleteUser(user);
		expectLastCall();
		ldapManager.shutdown();
		expectLastCall();
	}

	private LdapManager expectLdapBuild() {
		LdapManager ldapManager = mocksControl.createMock(LdapManager.class);

		expect(ldapService.buildManager()).andReturn(ldapManager);
		return ldapManager;
	}
	
	@Test
	public void testCreateUserWithNoEmail() throws DaoException, BatchNotFoundException, SQLException {
		Operation.Builder opBuilder = Operation
				.builder()
				.id(operationId(1))
				.status(BatchStatus.IDLE)
				.entityType(BatchEntityType.USER)
				.request(Request
						.builder()
						.url("/users/")
						.verb(HttpVerb.POST)
						.body(	"{" +
										"\"id\": \"extIdUser1\"," +
										"\"login\": \"user1\"," +
										"\"profile\": \"user\"," +
										"\"password\": \"secret\"" +
								"}")
						.build());
		Batch.Builder batchBuilder = Batch
				.builder()
				.id(batchId(1))
				.domain(domain)
				.status(BatchStatus.IDLE)
				.operation(opBuilder.build());
		Date date = DateUtils.date("2013-08-01T12:00:00");
		final ObmUser user = ObmUser
				.builder()
				.login("user1")
				.password("secret")
				.profileName(ProfileName.valueOf("user"))
				.extId(UserExtId.valueOf("extIdUser1"))
				.domain(domain)
				.build();

		expect(dateProvider.getDate()).andReturn(date).anyTimes();
		expect(userDao.create(user)).andReturn(user);
		expectLdapCreateUser(user);
		expect(batchDao.update(batchBuilder
				.operation(opBuilder
						.status(BatchStatus.SUCCESS)
						.timecommit(date)
						.build())
				.status(BatchStatus.SUCCESS)
				.timecommit(date)
				.build())).andReturn(null);
		
		mocksControl.replay();

		processor.process(batchBuilder.build());

		mocksControl.verify();
	}
	
	@Test
	public void testProcessDeleteUserWithFalseExpunge() throws SQLException, DaoException, BatchNotFoundException, UserNotFoundException {
		Operation.Builder opBuilder = Operation
				.builder()
				.id(operationId(1))
				.status(BatchStatus.IDLE)
				.entityType(BatchEntityType.USER)
				.request(Request
						.builder()
						.url("/users/extIdUser1")
						.param(Request.ITEM_ID_KEY, "extIdUser1")
						.verb(HttpVerb.DELETE)
						.build());
		Batch.Builder batchBuilder = Batch
				.builder()
				.id(batchId(1))
				.domain(domain)
				.status(BatchStatus.IDLE)
				.operation(opBuilder.build());
		Date date = DateUtils.date("2013-08-01T12:00:00");
		final ObmUser user = ObmUser
				.builder()
				.login("user1")
				.password("secret")
				.profileName(ProfileName.valueOf("user"))
				.extId(UserExtId.valueOf("extIdUser1"))
				.domain(domain)
				.build();

		expect(dateProvider.getDate()).andReturn(date).anyTimes();
		final UserExtId extId = UserExtId.valueOf("extIdUser1");
		expect(userDao.getByExtId(extId, domain)).andReturn(user);
		userDao.delete(extId);
		expectLastCall();
		expectLdapdeleteUser(user);
		expect(batchDao.update(batchBuilder
				.operation(opBuilder
						.status(BatchStatus.SUCCESS)
						.timecommit(date)
						.build())
				.status(BatchStatus.SUCCESS)
				.timecommit(date)
				.build())).andReturn(null);
		
		mocksControl.replay();

		processor.process(batchBuilder.build());

		mocksControl.verify();
	}
	
	@Test
	public void testProcessDeleteUserWithTrueExpunge()
			throws SQLException, DaoException, BatchNotFoundException, UserNotFoundException, IMAPException {
		Operation.Builder opBuilder = Operation
				.builder()
				.id(operationId(1))
				.status(BatchStatus.IDLE)
				.entityType(BatchEntityType.USER)
				.request(Request
						.builder()
						.url("/users/extIdUser1")
						.param(Request.ITEM_ID_KEY, "extIdUser1")
						.param(Request.EXPUNGE_KEY, "true")
						.verb(HttpVerb.DELETE)
						.build());
		Batch.Builder batchBuilder = Batch
				.builder()
				.id(batchId(1))
				.domain(domain)
				.status(BatchStatus.IDLE)
				.operation(opBuilder.build());
		Date date = DateUtils.date("2013-08-01T12:00:00");
		final ObmUser user = ObmUser
				.builder()
				.login("user1")
				.password("secret")
				.profileName(ProfileName.valueOf("user"))
				.extId(UserExtId.valueOf("extIdUser1"))
				.domain(domain)
				.mailHost(ObmHost.builder().name("host").build())
				.build();

		expect(dateProvider.getDate()).andReturn(date).anyTimes();
		final UserExtId extId = UserExtId.valueOf("extIdUser1");
		expect(userDao.getByExtId(extId, domain)).andReturn(user);
		userDao.delete(extId);
		expectLastCall();
		expectDeleteUserMailbox(user);
		expectLdapdeleteUser(user);
		expect(batchDao.update(batchBuilder
				.operation(opBuilder
						.status(BatchStatus.SUCCESS)
						.timecommit(date)
						.build())
				.status(BatchStatus.SUCCESS)
				.timecommit(date)
				.build())).andReturn(null);
		
		mocksControl.replay();

		processor.process(batchBuilder.build());

		mocksControl.verify();
	}
	
	private void expectDeleteUserMailbox(final ObmUser user) throws DaoException, IMAPException {
		CyrusManager cyrusManager = expectCyrusBuild();
		cyrusManager.setAcl(user, "cyrus", Acl.builder().user("user1").rights("lc").build());
		expectLastCall().once();
		cyrusManager.delete(user);
		expectLastCall().once();
		expectCyrusShutDown(cyrusManager);
	}

	private void expectCyrusShutDown(CyrusManager cyrusManager) {
		cyrusManager.shutdown();
		expectLastCall().once();
	}

	private CyrusManager expectCyrusBuild() throws DaoException, IMAPException {
		expect(userSystemDao.getByLogin("cyrus")).andReturn(obmCyrusUser);
		CyrusManager cyrusManager = mocksControl.createMock(CyrusManager.class);
		expect(cyrusService.buildManager("host", "cyrus", "secret")).andReturn(cyrusManager);
		return cyrusManager;
	}
}