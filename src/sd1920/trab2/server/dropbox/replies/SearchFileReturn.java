package sd1920.trab2.server.dropbox.replies;

import java.util.HashMap;
import java.util.List;


public class SearchFileReturn {

    private boolean has_more;
	private List<FileEntry> matches;
	
	public static class FileEntry extends HashMap<String, Object> {
		private static final long serialVersionUID = 1L;
		public FileEntry() {}
		
		@Override
		public String toString() {
			return super.get("path_display").toString();
		}
	}
	
	public SearchFileReturn() {	
	}

	public boolean has_more() {
		return has_more;
	}

	public void setHas_more(boolean has_more) {
		this.has_more = has_more;
	}

	public List<FileEntry> getEntries() {
		return matches;
    }
    
    public boolean foundFile(){
        return matches.size() == 1;
    }

	/*public void setEntries(List<FolderEntry> entries) {
		this.entries = entries;
	}*/
}