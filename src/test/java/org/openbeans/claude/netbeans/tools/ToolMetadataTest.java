package org.openbeans.claude.netbeans.tools;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the MCP tool names against accidental renames — a name change breaks
 * the protocol contract with Claude Code CLI.
 */
public class ToolMetadataTest {

    @Test public void checkDocumentDirty()    { assertEquals("checkDocumentDirty",    new CheckDocumentDirty().getName()); }
    @Test public void closeAllDiffTabs()      { assertEquals("closeAllDiffTabs",      new CloseAllDiffTabs().getName()); }
    @Test public void closeTab()              { assertEquals("close_tab",             new CloseTab().getName()); }
    @Test public void getCurrentSelection()   { assertEquals("getCurrentSelection",   new GetCurrentSelection().getName()); }
    @Test public void getDiagnostics()        { assertEquals("getDiagnostics",        new GetDiagnostics().getName()); }
    @Test public void getOpenEditors()        { assertEquals("getOpenEditors",        new GetOpenEditors().getName()); }
    @Test public void getWorkspaceFolders()   { assertEquals("getWorkspaceFolders",   new GetWorkspaceFolders().getName()); }
    @Test public void openDiff()              { assertEquals("openDiff",              new OpenDiff().getName()); }
    @Test public void openFile()              { assertEquals("openFile",              new OpenFile().getName()); }
    @Test public void saveDocument()          { assertEquals("saveDocument",          new SaveDocument().getName()); }
}
