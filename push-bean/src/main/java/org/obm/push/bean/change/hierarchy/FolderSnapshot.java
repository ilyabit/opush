/* ***** BEGIN LICENSE BLOCK *****
 * 
 * Copyright (C) 2015 Linagora
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
package org.obm.push.bean.change.hierarchy;

import java.util.Set;

import org.obm.push.bean.BackendId;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class FolderSnapshot {
	
	public static final int FIRST_NEXT_ID = 1;
	
	public static FolderSnapshot empty() {
		return new Builder(FIRST_NEXT_ID).folders(ImmutableSet.<Folder>of());
	}
	
	public static FolderSnapshot.Builder nextId(int nextId) {
		return new Builder(nextId);
	}
	
	public static class Builder {
		
		private Integer nextId;

		private Builder(int nextId) {
			Preconditions.checkArgument(nextId > 0, "nextId must be a positive integer");
			this.nextId = nextId;
		}
		
		public FolderSnapshot folders(Set<Folder> folders) {
			Preconditions.checkNotNull(nextId, "nextId must be a positive integer");
			Preconditions.checkNotNull(folders, "folders can't be null");
			return new FolderSnapshot(nextId, ImmutableSet.copyOf(folders));
		}
	}
	
	private final int nextId;
	private final Set<Folder> folders;
	private final ImmutableMap<BackendId.Id, Folder> foldersByBackendId;

	private FolderSnapshot(int nextId, Set<Folder> folders) {
		this.nextId = nextId;
		this.folders = folders;
		this.foldersByBackendId = Folder.mapByBackendId(folders);
	}

	public int getNextId() {
		return nextId;
	}

	public Set<Folder> getFolders() {
		return folders;
	}

	public ImmutableMap<BackendId.Id, Folder> getFoldersByBackendId() {
		return foldersByBackendId;
	}

	@Override
	public final int hashCode(){
		return Objects.hashCode(nextId, folders);
	}
	
	@Override
	public final boolean equals(Object object){
		if (object instanceof FolderSnapshot) {
			FolderSnapshot that = (FolderSnapshot) object;
			return Objects.equal(this.nextId, that.nextId)
				&& Objects.equal(this.folders, that.folders);
		}
		return false;
	}

	@Override
	public String toString() {
		return Objects.toStringHelper(this)
			.add("nextId", nextId)
			.add("folders", folders)
			.toString();
	}
}