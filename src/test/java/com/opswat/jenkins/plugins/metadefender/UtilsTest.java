package com.opswat.jenkins.plugins.metadefender;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers the file-listing code that used org.apache.commons.lang.ArrayUtils, which
 * Jenkins core stopped providing in 2.579 (NoClassDefFoundError at Utils.java:22).
 * These tests run without a Jenkins instance, so they fail fast if the class ever
 * regains an undeclared dependency on a core-provided library.
 */
public class UtilsTest {

    @Rule
    public TemporaryFolder workspace = new TemporaryFolder();

    private Set<String> namesOf(ArrayList<File> files) {
        Set<String> names = new HashSet<>();
        for (File f : files) {
            names.add(f.getName());
        }
        return names;
    }

    @Test
    public void listFilesRecursivelyWithNullExcludeListsEverything() throws IOException {
        workspace.newFile("a.txt");
        File sub = workspace.newFolder("sub");
        new File(sub, "b.txt").createNewFile();

        ArrayList<File> out = new ArrayList<>();
        Utils.listFilesRecursively(workspace.getRoot().getAbsolutePath(), out, null);

        assertEquals(namesOf(out).toString(), 2, out.size());
        assertTrue(namesOf(out).contains("a.txt"));
        assertTrue(namesOf(out).contains("b.txt"));
    }

    @Test
    public void listFilesRecursivelyWithEmptyExcludeListsEverything() throws IOException {
        workspace.newFile("a.txt");

        ArrayList<File> out = new ArrayList<>();
        Utils.listFilesRecursively(workspace.getRoot().getAbsolutePath(), out, new String[0]);

        assertEquals(1, out.size());
    }

    @Test
    public void listFilesRecursivelySkipsExcludedDirectory() throws IOException {
        workspace.newFile("a.txt");
        File git = workspace.newFolder(".git");
        new File(git, "config").createNewFile();

        ArrayList<File> out = new ArrayList<>();
        Utils.listFilesRecursively(workspace.getRoot().getAbsolutePath(), out,
                new String[]{git.getAbsolutePath()});

        assertEquals(namesOf(out).toString(), 1, out.size());
        assertTrue(namesOf(out).contains("a.txt"));
        assertFalse(namesOf(out).contains("config"));
    }

    @Test
    public void listFilesRecursivelySkipsWhenSourceItselfIsExcluded() throws IOException {
        File file = workspace.newFile("a.txt");

        ArrayList<File> out = new ArrayList<>();
        Utils.listFilesRecursively(file.getAbsolutePath(), out,
                new String[]{file.getAbsolutePath()});

        assertTrue(out.toString(), out.isEmpty());
    }

    @Test
    public void createFileListHonoursExcludedFolder() throws IOException {
        File src = workspace.newFolder("src");
        new File(src, "main.c").createNewFile();
        File idea = new File(src, ".idea");
        assertTrue(idea.mkdir());
        new File(idea, "workspace.xml").createNewFile();

        ArrayList<File> out = Utils.createFileList("src", "src" + File.separator + ".idea",
                workspace.getRoot().getAbsolutePath());

        assertEquals(namesOf(out).toString(), 1, out.size());
        assertTrue(namesOf(out).contains("main.c"));
    }

    @Test
    public void createFileListDeduplicatesOverlappingSources() throws IOException {
        File src = workspace.newFolder("src");
        new File(src, "main.c").createNewFile();

        ArrayList<File> out = Utils.createFileList("src|src", "", workspace.getRoot().getAbsolutePath());

        assertEquals(namesOf(out).toString(), 1, out.size());
    }

    @Test
    public void createScanResultLinkForCloudAndLocal() {
        assertEquals("https://metadefender.opswat.com/results/file/abc/regular/overview?lang=en",
                Utils.createScanResultLink("https://api.metadefender.com/v4/file", "abc"));
        assertEquals("http://localhost:8008/#/public/process/dataId/abc",
                Utils.createScanResultLink("http://localhost:8008/file", "abc"));
    }
}
