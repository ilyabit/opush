package org.obm.push.mail;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.obm.configuration.EmailConfiguration;
import org.obm.push.bean.FolderType;
import org.obm.push.bean.change.hierarchy.BackendFolder;
import org.obm.push.bean.change.hierarchy.BackendFolders;
import org.obm.push.mail.bean.MailboxFolder;
import org.obm.push.mail.bean.MailboxFolders;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeTraverser;


public class MailBackendFoldersBuilder {
	
	private static final ImmutableMap<String, FolderType> SPECIAL_FOLDERS_TYPES = ImmutableMap.of(
		EmailConfiguration.IMAP_INBOX_NAME, FolderType.DEFAULT_INBOX_FOLDER,
		EmailConfiguration.IMAP_DRAFTS_NAME, FolderType.DEFAULT_DRAFTS_FOLDER,
		EmailConfiguration.IMAP_SENT_NAME, FolderType.DEFAULT_SENT_EMAIL_FOLDER,
		EmailConfiguration.IMAP_TRASH_NAME, FolderType.DEFAULT_DELETED_ITEMS_FOLDER);

	private Set<MailboxPath> mailboxes;
	private Set<MailboxPath> specialMailboxes;
	
	public MailBackendFoldersBuilder() {
		mailboxes = Sets.newHashSet();
		specialMailboxes = Sets.newHashSet();
	}
	
	public MailBackendFoldersBuilder addFolders(MailboxFolders folders) {
		for (MailboxFolder folder : folders) {
			mailboxes.add(MailboxPath.of(folder.getName(), folder.getImapSeparator()));
		}
		return this;
	}
	
	public MailBackendFoldersBuilder addSpecialFolders(Collection<String> folders) {
		for (String folder : folders) {
			specialMailboxes.add(MailboxPath.of(folder));
		}
		return this;
	}
	
	public BackendFolders<MailboxPath> build() {
		Map<MailboxPath, Node> pathToNode = Maps.newHashMap(); 
		Node root = new Node();
		
		// Handle special mailboxes, don't search for parent
		for (MailboxPath folder : specialMailboxes) {
			Node node = new Node(BackendFolder.<MailboxPath>builder()
				.displayName(folder.asString())
				.folderType(folderType(folder))
				.backendId(folder)
				.parentId(Optional.<MailboxPath>absent())
				.build());
			
			root.children.add(node);
			pathToNode.put(folder, node);
		}
		
		for (MailboxPath folder : mailboxes) {
			Entry<Node, Optional<MailboxPath>> searchParent = searchParent(pathToNode, root, folder);
			Node node = new Node(BackendFolder.<MailboxPath>builder()
				.displayName(folder.asString())
				.folderType(folderType(folder))
				.backendId(folder)
				.parentId(searchParent.getValue())
				.build());
			
			searchParent.getKey().children.add(node);
			pathToNode.put(folder, node);
		}
		return new TreeBackendFolders(root);
	}
	
	private Entry<Node, Optional<MailboxPath>> searchParent(Map<MailboxPath, Node> pathToNode,
			Node root, MailboxPath folder) {
		
		Optional<MailboxPath> find = Iterables.tryFind(folder.reducingPaths(), Predicates.in(pathToNode.keySet()));
		if (!find.isPresent()) {
			return Maps.immutableEntry(root, find);
		}
		return Maps.immutableEntry(pathToNode.get(find.get()), find);
	}

	private FolderType folderType(MailboxPath path) {
		return Objects.firstNonNull(SPECIAL_FOLDERS_TYPES.get(path.asString()), FolderType.USER_CREATED_EMAIL_FOLDER);
	}
	
	private static class Node {
		
		final Set<Node> children;
		final Optional<BackendFolder<MailboxPath>> value;

		private Node() {
			this(null);
		}
		
		private Node(BackendFolder<MailboxPath> value) {
			this.value = Optional.fromNullable(value);
			this.children = Sets.newHashSet();
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(value, children);
		}
		
		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Node) {
				Node that = (Node)obj;
				return Objects.equal(value, that.value)
					&& Objects.equal(children, that.children);
			}
			return false;
		}
	
		@Override
		public String toString() {
			return Objects.toStringHelper(this)
				.add("value", value)
				.add("children", children)
				.toString();
		}
	}

	private static class TreeBackendFolders implements BackendFolders<MailboxPath> {
		
		private final Node root;
		
		private TreeBackendFolders(Node root) {
			this.root = root;
		}
	
		@Override
		public Iterator<BackendFolder<MailboxPath>> iterator() {
			return new TreeTraverser<Node>() {
	
				@Override
				public Iterable<Node> children(Node node) {
					return node.children;
				}
			}
			.breadthFirstTraversal(root)
			.filter(new Predicate<Node>() {
	
				@Override
				public boolean apply(Node input) {
					return input.value.isPresent();
				}
			})
			.transform(new Function<Node, BackendFolder<MailboxPath>>() {
	
				@Override
				public BackendFolder<MailboxPath> apply(Node input) {
					return input.value.get();
				}
			}).iterator();
		}
	
		@Override
		public String toString() {
			return Objects.toStringHelper(this)
				.add("root", root)
				.toString();
		}
	}	
}