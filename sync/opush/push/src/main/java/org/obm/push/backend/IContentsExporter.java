package org.obm.push.backend;

import java.util.Collection;
import java.util.List;

import org.minig.imap.IMAPException;
import org.obm.push.bean.BackendSession;
import org.obm.push.bean.FilterType;
import org.obm.push.bean.ItemChange;
import org.obm.push.bean.MSAttachementData;
import org.obm.push.bean.PIMDataType;
import org.obm.push.bean.SyncCollection;
import org.obm.push.bean.SyncState;
import org.obm.push.exception.DaoException;
import org.obm.push.exception.UnknownObmSyncServerException;
import org.obm.push.exception.activesync.CollectionNotFoundException;
import org.obm.push.exception.activesync.AttachementNotFoundException;
import org.obm.sync.auth.ServerFault;

/**
 * The exporter API fetches data from the backend store and returns it to the
 * mobile device
 */
public interface IContentsExporter {

	DataDelta getChanged(BackendSession bs, SyncState state, FilterType filterType, Integer collectionId) 
			throws DaoException, CollectionNotFoundException, UnknownObmSyncServerException;

	int getCount(BackendSession bs, SyncState state, FilterType filterType, Integer collectionId) 
			throws DaoException, CollectionNotFoundException, UnknownObmSyncServerException;

	List<ItemChange> fetch(BackendSession bs, PIMDataType getDataType,
			List<String> fetchIds) throws CollectionNotFoundException, DaoException, ServerFault, UnknownObmSyncServerException ;
	
	List<ItemChange> fetchEmails(BackendSession bs,
			Integer collectionId, Collection<Long> uids) throws DaoException, CollectionNotFoundException;
	
	List<ItemChange> fetchCalendars(BackendSession bs, Integer collectionId, Collection<String> uids) throws UnknownObmSyncServerException ;

	List<ItemChange> fetchCalendarDeletedItems(BackendSession bs,Integer collectionId, Collection<String> uids) 
			throws UnknownObmSyncServerException ;
	
	MSAttachementData getEmailAttachement(BackendSession bs,
			String attachmentName) throws AttachementNotFoundException, CollectionNotFoundException, DaoException, IMAPException;

	boolean validatePassword(String userID, String password);

	boolean getFilterChanges(BackendSession bs, SyncCollection collection) throws DaoException;
	
}
