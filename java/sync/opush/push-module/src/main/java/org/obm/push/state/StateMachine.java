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
package org.obm.push.state;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.obm.push.bean.Device;
import org.obm.push.bean.FolderSyncState;
import org.obm.push.bean.ItemSyncState;
import org.obm.push.bean.ServerId;
import org.obm.push.bean.SyncState;
import org.obm.push.bean.UserDataRequest;
import org.obm.push.bean.change.item.ItemChange;
import org.obm.push.exception.DaoException;
import org.obm.push.exception.activesync.InvalidServerId;
import org.obm.push.exception.activesync.InvalidSyncKeyException;
import org.obm.push.store.CollectionDao;
import org.obm.push.store.ItemTrackingDao;
import org.obm.push.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class StateMachine {

	private static final Logger logger = LoggerFactory.getLogger(StateMachine.class);
	
	private final CollectionDao collectionDao;
	private final ItemTrackingDao itemTrackingDao;
	private final SyncKeyFactory syncKeyFactory;

	@Inject
	@VisibleForTesting StateMachine(CollectionDao collectionDao, ItemTrackingDao itemTrackingDao,
			SyncKeyFactory syncKeyFactory) {
		this.collectionDao = collectionDao;
		this.itemTrackingDao = itemTrackingDao;
		this.syncKeyFactory = syncKeyFactory;
	}

	public ItemSyncState lastKnownState(Device device, Integer collectionId) throws DaoException {
		return collectionDao.lastKnownState(device, collectionId);
	}
	
	public ItemSyncState getItemSyncState(String syncKey) throws DaoException {
		return collectionDao.findItemStateForKey(syncKey);
	}
	
	public FolderSyncState getFolderSyncState(String syncKey) throws DaoException, InvalidSyncKeyException {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(syncKey));
		
		if (FolderSyncState.isSyncKeyOfInitialFolderSync(syncKey)) {
			return new FolderSyncState(syncKey);
		} else {
			return findFolderSyncState(syncKey);
		}
	}

	private FolderSyncState findFolderSyncState(String syncKey) throws DaoException, InvalidSyncKeyException {
		FolderSyncState folderSyncStateForKey = collectionDao.findFolderStateForKey(syncKey);
		if (folderSyncStateForKey == null) {
			throw new InvalidSyncKeyException(syncKey);
		}
		return folderSyncStateForKey;
	}
	
	public FolderSyncState allocateNewFolderSyncState(UserDataRequest udr) throws DaoException {
		String newSk = syncKeyFactory.randomSyncKey();
		FolderSyncState newFolderState = collectionDao.allocateNewFolderSyncState(udr.getDevice(), newSk);
		newFolderState.setLastSync(DateUtils.getCurrentDate());
		
		log(udr, newFolderState);
		return newFolderState;
	}
	
	public String allocateNewSyncKey(UserDataRequest udr, Integer collectionId, Date lastSync, 
		Collection<ItemChange> changes, Collection<ItemChange> deletedItems) throws DaoException, InvalidServerId {

		String newSk = syncKeyFactory.randomSyncKey();
		ItemSyncState newState = new ItemSyncState(newSk, lastSync);
		int syncStateId = collectionDao.updateState(udr.getDevice(), collectionId, newState);
		newState.setId(syncStateId);
		
		if (changes != null && !changes.isEmpty()) {
			itemTrackingDao.markAsSynced(newState, listNewItems(changes));
		}
		if (deletedItems != null && !deletedItems.isEmpty()) {
			itemTrackingDao.markAsDeleted(newState, itemChangesAsServerIdSet(deletedItems));
		}
		
		log(udr, newState);
		return newSk;
	}

	private Set<ServerId> itemChangesAsServerIdSet(Iterable<ItemChange> changes) throws InvalidServerId {
		Set<ServerId> ids = Sets.newHashSet();
		for (ItemChange change: changes) {
			addServerItemId(ids, change);
		}
		return ids;
	}

	private Set<ServerId> listNewItems(Collection<ItemChange> changes) throws InvalidServerId {
		HashSet<ServerId> serverIds = Sets.newHashSet();
		for (ItemChange change: changes) {
			if (change.isNew()) {
				addServerItemId(serverIds, change);
			}
		}
		return serverIds;
	}

	private void addServerItemId(Set<ServerId> serverIds, ItemChange change) throws InvalidServerId {
		ServerId serverId = new ServerId( change.getServerId() );
		if (serverId.isItem()) {
			serverIds.add(serverId);
		}
	}
	
	private void log(UserDataRequest udr, SyncState newState) {
		String collectionPath = "obm:\\\\" + udr.getUser().getLoginAtDomain();
		logger.info("Allocate new synckey {} for collectionPath {} with {} last sync", 
				new Object[]{newState.getKey(), collectionPath, newState.getLastSync()});
	}
	
}
