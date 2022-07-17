/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package diskusagetest;

import com.sun.jna.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinNT.HANDLE;

import java.io.IOException;
import java.io.IOError;

import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jerry
 */
public class DiskUsageTest {

    /**
     * Operating System Specific Details encapsulated here
     */
    public static OS myOS;
    public static double m_kb = 1024.0;
    private static boolean debug = false;
    private static boolean verbose = false;

    /**
     * Given a filesystem pathname, return associated Java File Store
     * @param termType
     * @param fileString
     * @return Associated MyFileSystem
     */
    private static MyFileSystem getJavaFileStore(TermType termType, String fileString) {
        MyFileSystem ret;
            try {
                Path path = Paths.get(fileString).normalize();
                FileStore store = Files.getFileStore(path);
                if (debug) System.out.printf("FileStore mount point '%s', ", path);
                ret = new MyFileSystem(termType, myOS, store, m_kb, verbose, debug);
            } catch (IOException ex) {
                String msg = "No Valid Filesystem for " + fileString + ": " + ex;
                if (verbose) System.out.println(msg);
                if (debug) ex.printStackTrace(System.out);
                ret = new MyFileSystem(termType, myOS, msg, m_kb, verbose, debug);
            } catch (IOError ex2) {
                String msg = "Bad Path '" + fileString + "' " + ex2;
                if (verbose)  System.out.println(msg);
                ret = new MyFileSystem(termType, myOS, msg, m_kb, verbose, debug);
            } catch (Throwable ex3) {
                String msg = "Unknown Exception '" + fileString + "' " + ex3;
                if (verbose) System.out.println(msg);
                if (debug) ex3.printStackTrace(System.out);
                ret = new MyFileSystem(termType, myOS, msg, m_kb, verbose, debug);
            }
            return ret;
    }
    
    /***
     * On some Linux systems, notably WSL, Java is not returning the set of
     * file stores. This is a hack.
     * @param term
     * @return Associated MyFileSystem
     * @throws Exception 
     */
    private static List<MyFileSystem> getAllFilesystemRootsFromLinux(TermType term)
            throws Exception {
        List<MyFileSystem> mfs = new ArrayList<>();
        if (myOS.getOS() == OS.OSTypes.LINUX) {
            String foobar = "/bin/sh -c 'mount'";
            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();
            int ex = OS.execCommand(foobar, out, err);
            if (ex != 0) {
                System.out.printf("Cannot execute mount command\n");
                return null;
            }

            String[] locs = out.toString().split("\\R");
            for (String me : locs) {
                // System.out.printf("Line '%s'\n", me);
                String[] words = me.split("\\s+");
                String wordfs = words[2];
                // System.out.printf("\tmount point is '%s'\n", wordfs);
                if (verbose) System.out.printf("Hacked Mount Point: %-30.30s: ", wordfs);
                MyFileSystem afs = getJavaFileStore(term, wordfs);
                mfs.add(afs);
            }
            return mfs;
        } else {
            System.out.printf("Cannot get list of mounted filesystems, aborting.\n");
            return null;
        }
    }

    /**
     * On Windows, in order to get ANSI escape sequence colors, this is needed
     * @param myOS 
     */
    private static void setWindowsConsoleMode(OS myOS) {
        if (debug) System.out.printf("setting Windows Console Mode: myOS.getOS() = '%s' ? ", myOS.getOS());
        if (myOS.getOS() == OS.OSTypes.WINDOWS) {
            if (debug) System.out.printf("YES\n");
            // Set output mode to handle virtual terminal sequences
            Function GetStdHandleFunc = Function.getFunction("kernel32", "GetStdHandle");
            DWORD STD_OUTPUT_HANDLE = new DWORD(-11);
            HANDLE hOut = (HANDLE) GetStdHandleFunc.invoke(HANDLE.class, new Object[] { STD_OUTPUT_HANDLE });

            DWORDByReference p_dwMode = new DWORDByReference(new DWORD(0));
            Function GetConsoleModeFunc = Function.getFunction("kernel32", "GetConsoleMode");
            GetConsoleModeFunc.invoke(BOOL.class, new Object[] { hOut, p_dwMode });

            int ENABLE_VIRTUAL_TERMINAL_PROCESSING = 4;
            DWORD dwMode = p_dwMode.getValue();
            dwMode.setValue(dwMode.intValue() | ENABLE_VIRTUAL_TERMINAL_PROCESSING);
            Function SetConsoleModeFunc = Function.getFunction("kernel32", "SetConsoleMode");
            SetConsoleModeFunc.invoke(BOOL.class, new Object[] { hOut, dwMode });
        } else {
            if (debug) System.out.printf("NO\n");
        }
    }

    /**
     * @param args the command line arguments
     * @throws java.lang.Exception
     */
    public static void main(String[] args)
            throws Exception 
    {
        // Setup Terminal Environment
        myOS = new OS();
        AnsiCodes.ANSI_COLOR color = AnsiCodes.ANSI_COLOR.getMin();
        TermType          termType = new TermType(myOS);
        
        // Intialize list of FileSystem Types and FileSystem mounts to filter
        List<String> fsTypes = new ArrayList<>();
        List<String> fsLocations = new ArrayList<>();
        
        // Parse arguments
        boolean[]        flags = parseCommandLineArgs(args, fsTypes, fsLocations);
        boolean    localFSOnly = flags[0];
        boolean  specialFSAlso = flags[1];
        boolean        summary = flags[2];
        int              count = 0;
        boolean allFileSystems = false;
        String              header = buildHeader(termType); // Build O.S. Specific Header
        setWindowsConsoleMode(myOS); // Windows requires special handling for color/lines        
        
       
        /* If user did not specify a set of perspective 'mount' points
         * than determine the set. Not all O.Ses (WSL so far) work for this
        */
        List<MyFileSystem> allOSFileSystems = new ArrayList<>();
        if (fsLocations.isEmpty()) {
            allOSFileSystems = getAllFilesystemRootFromJava(termType);
            if (verbose) System.out.printf("Number of filesystems returned from OS: %d\n", allOSFileSystems.size());
            if (allOSFileSystems == null || allOSFileSystems.isEmpty()) {
                if (verbose) System.out.printf("No File Systems returned from O.S., try hack\n");
                allOSFileSystems = getAllFilesystemRootsFromLinux(termType);
		if (allOSFileSystems == null || allOSFileSystems.isEmpty()) {
                    System.out.printf("Complete Failure, try wildcards!\n");
                    noResult(termType, header, myOS);
                    return;
                }
	    }
        }
        
        // Given the list, filter it
        try {
            if (fsLocations.isEmpty()) {
                count = filterDisks(header, termType, 
                        fsTypes, allOSFileSystems, 
                        localFSOnly, specialFSAlso, 
                        color, summary);
            } else {                
		count = filterDisks(header, termType, 
				    fsTypes, fsLocations, 
				    localFSOnly, specialFSAlso, 
				    color, summary, allFileSystems);
            }
        }
        catch (Exception ex) {
            System.out.println(AnsiCodes.getReset(termType));
            ex.printStackTrace(System.err);
        }
        
        if (count == 0) noResult(termType, header, myOS);
	System.out.println(AnsiCodes.getReset(termType));
    }

    private static void noResult(TermType termType, String header, OS myOS) {
        String msg = "No Filesystems matched Specification";
        MyFileSystem errorMF = new MyFileSystem(termType, myOS, msg, m_kb, verbose, debug);
        AnsiCodes.ANSI_COLOR color = AnsiCodes.ANSI_COLOR.RED;
        System.out.println(line(termType.getActualColumns(), termType, 
                termType.TOP_LEFT_CORNER,
                termType.TOP_RIGHT_CORNER));
        System.out.print(header);
        System.out.println(line(termType.getActualColumns(), termType, 
                termType.LEFT_COLUMN_LINE,
                termType.RIGHT_COLUMN_LINE));

        System.out.printf("%s%s", AnsiCodes.getReset(termType), termType.VERTICAL_BAR);
        System.out.printf("%s%s%s%s\n", color.getBoldCode(termType), formatDiskUsage(errorMF, termType),
                AnsiCodes.getReset(termType), termType.VERTICAL_BAR);
        System.out.println(line(termType.getActualColumns(), termType,
                termType.BOTTOM_LEFT_CORNER, termType.BOTTOM_RIGHT_CORNER));
    }

    /**
     * Build the header, based on Operating System, Terminal Type, and Language.
     * 
     * @param termType
     * @return
     */
    private static String buildHeader(TermType termType) {
        long minColumnWidth = termType.getMinColumnWidth();
        long actualColumns = termType.getActualColumns();
        // Headers for different terminal types and terminal column widths
        String LONG_ANSI_HEADER = String.format(
                "%s%s%-20.20s %-36.36s %4s %4s %14.14s %14.14s %14.14s %7.7s %12.12s%s%s\n",
                termType.VERTICAL_BAR,
                AnsiCodes.getBold(termType),
                "Device", "Mount Point", "Disk", "Part", "Total", "Used", "Usable", "Used %", "Type",
                AnsiCodes.getReset(termType),
                termType.VERTICAL_BAR);

        String LONG_DOS_ANSI_HEADER = String.format("%s%s%-20.20s %-5.5s %14.14s %14.14s %14.14s %7.7s %12.12s%s%s\n",
                termType.VERTICAL_BAR,
                AnsiCodes.getBold(termType),
                "Volume", "Drive", "Total", "Used", "Usable", "Used %", "Type",
                AnsiCodes.getReset(termType),
                termType.VERTICAL_BAR);

       // String LONG_DOS_HEADER = String.format("%-30.30s %-5.5s %14.14s %14.14s %14.14s %7.7s %12.12s\n",
       //         "Device", "Drive", "Total", "Used", "Usable", "Used %", "Type");

        String SHORT_ANSI_HEADER = String.format("%s%s%-20.20s %-19.19s %14.14s %14.14s %7.7s%s%s\n",
                termType.VERTICAL_BAR,
                AnsiCodes.getBold(termType),
                "Device", "Mount Point", "Total", "Usable", "Used %",
                AnsiCodes.getReset(termType),
                termType.VERTICAL_BAR);
        String SHORT_DOS_ANSI_HEADER = String.format("%s%s%-20.20s %-5.5s %14.14s %14.14s %7.7s%s%s\n",
                termType.VERTICAL_BAR,
                AnsiCodes.getBold(termType),
                "Volume", "Drive", "Total", "Usable", "Used %",
                AnsiCodes.getReset(termType),
                termType.VERTICAL_BAR);

        String SHORT_DOS_HEADER = String.format("%s%-20.20s %-5.5s %14.14s %14.14s%s\n",
                termType.VERTICAL_BAR,
                "Device", "Drive", "Total", "Usable",
                termType.VERTICAL_BAR);
        String head;

        if (actualColumns < minColumnWidth) {
            if (myOS.getOS() == OS.OSTypes.WINDOWS) {
                if (termType.isAnsiTerm()) {
                    head = SHORT_DOS_ANSI_HEADER;
                    if (debug) System.out.printf("Built Header: SHORT_DOS_ANSI_HEADER\n");
                } else {
                    head = SHORT_DOS_HEADER;
                    if (debug) System.out.printf("Built Header: SHORT_DOS_HEADER\n");
                }
            } else {
                head = SHORT_ANSI_HEADER;
                if (debug) System.out.printf("Built Header: SHORT_ANSI_HEADER\n");
            }
        } else {
            if (myOS.getOS() == OS.OSTypes.WINDOWS) {
                head = LONG_DOS_ANSI_HEADER;
                if (debug) System.out.printf("Built Header: LONG_DOS_ANSI_HEADER\n");
            } else {
                head = LONG_ANSI_HEADER;
                if (debug) System.out.printf("Built Header: LONG_ANSI_HEADER\n");            
            }
        }
        return head;
    }

    /*
     * Provide colorized output, in an operating system independent manner, of the
     * specified
     * file stores.
     * 
     * The following rules are obeyed:
     * 
     * All entries are sorted by physical disk and partition, using appropriate
     * naming
     * conventions for the Operating System.
     * For Example:
     * /dev/sda1
     * /dev/sda2
     * /dev/sdb1
     * etc.
     * 
     * For every new physical device, a horizontal line is drawn. So, for example:
     * On Linux, between all sdX devices and sdY devices there is a line.
     * For Mac, between diskX and diskY, there is a line.
     * 
     * Within a given physical disk, entries are colorized. The first entry is
     * always red (31), and
     * everytime a new filesystem type follows, the color is incremented.
     * 
     * This allows easy identification of various partitions.
     * 
     * There are several variations on disks/partitions:
     * Logical Volumes (Linux)
     * Raid Volumes (Linux)
     * Networked Devices (Linux, Mac, Windows)
     * Special Devices that have storage associated with them.
     * Special devices without storage associated.
     * 
     */
    private static void colorizeDiskUsage(List<MyFileSystem> myFileSystems,
            TermType termType,
            AnsiCodes.ANSI_COLOR color, boolean summary)

    {

        try {
            Collections.sort(myFileSystems);
            String lastDiskUnit = "n/a";
            String lastDiskType = "n/a";
            boolean lastSpecialDevice = false;
            boolean lastNonStorageDevice = false;
            boolean lastNetworkedDevice = false;
            boolean lastLogicalDeviceType = false;
            long totalSpace = 0;
            long totalFreeSpace = 0;
            boolean rootFileSystemDone = false;
            int rowsProcessed = 0;
            String priorName = null;
            for (int ent = 0; ent < myFileSystems.size(); ent++) {
                MyFileSystem myFS = myFileSystems.get(ent);
                String name = myFS.getName();
                String type = myFS.getType();
                String unit = myFS.getDiskUnit();
                String dev = myFS.getDevice();
                if (!name.equals(priorName)) {
                    if (debug) System.out.printf("Colorizing Entry #%d, name = '%s', type = '%s', unit = '%s'\n",
                                                 ent, name, type, unit);
                    
                } else {
                    if (verbose) System.out.printf("Duplicate Device Entry '%s', ingoring\n", name);
                    continue;
                }
                priorName = name;

                boolean specialDevice = myFS.isSpecialDevice();
                boolean nonStorageDevice = myFS.isNonStorageDevice();
                boolean networkedDevice = myFS.isNFSDevice();

                totalSpace += myFS.m_totalSpace;
                totalFreeSpace += myFS.m_usableSpace;

                String out = formatDiskUsage(myFS, termType);

                if ((type.equals("rootfs") && rootFileSystemDone) ||
                        (name.equals("/") && rootFileSystemDone) ||
                        (dev.equals("rootfs") && rootFileSystemDone)) {
                    continue;
                }

                if (type.equals("rootfs") || name.equals("/") || dev.equals("rootfs")) {
                    rootFileSystemDone = true;
                    name = "/";
                    specialDevice = false;
                }

                boolean logicalDeviceType = (myFS.isRaidDisk() || myFS.isLogicalDisk());
                if (!"".equals(out)) {
                    if (((!type.equals(lastDiskType) ||
                            (lastSpecialDevice != specialDevice)) ||
                            (logicalDeviceType != lastLogicalDeviceType) ||
                            (!unit.equals(lastDiskUnit))))

                    {
                        if (((nonStorageDevice != lastNonStorageDevice) || (lastSpecialDevice != specialDevice)) ||
                                ((networkedDevice != lastNetworkedDevice) ||
                                        (logicalDeviceType != lastLogicalDeviceType) ||
                                        (lastSpecialDevice != specialDevice) ||
                                        (!unit.equals(lastDiskUnit)))) {
                            if (ent > 0) {
                                System.out.print(line(out.length(), termType));
                                System.out.println("\r");
                                color = AnsiCodes.ANSI_COLOR.getMin();
                            }
                        }

                        color = AnsiCodes.ANSI_COLOR.next(color);
                    }
                    lastNonStorageDevice = nonStorageDevice;
                    lastSpecialDevice = specialDevice;
                    lastNetworkedDevice = networkedDevice;
                    lastLogicalDeviceType = logicalDeviceType;
                    lastDiskUnit = unit;
                    lastDiskType = type;
                }
                if (out != null) {
                    System.out.printf("%s%s", AnsiCodes.getReset(termType), termType.VERTICAL_BAR);
                    if (!myFS.isBadFilesystem()) {
                        System.out.printf(color.getCode(termType));
                    }
                    System.out.printf("%s", out);
                    System.out.printf("%s%s\n", AnsiCodes.getReset(termType), termType.VERTICAL_BAR);
                    rowsProcessed++;
                }
            }

            if (summary && (rowsProcessed > 0)) {
                String total = MyFileSystem.normalizeValue(totalSpace, m_kb);
                String used = MyFileSystem.normalizeValue(totalSpace - totalFreeSpace, m_kb);
                String free = MyFileSystem.normalizeValue(totalFreeSpace, m_kb);
                String summaryString = formatOutput(termType,
                        "All Devices", "All Mount Points", "---", "---",
                        total, used, free,
                        MyFileSystem.getPercentage(totalSpace, totalFreeSpace),
                        "---", false, false, false);
                System.out.println(line(summaryString.length() - 1, termType));
                System.out.printf("\033[0m%s\033[42;1m", termType.VERTICAL_BAR);
                System.out.print(summaryString);
                System.out.printf("\033[0m%s\n", termType.VERTICAL_BAR);
            }
            if (rowsProcessed == 0) {
                System.out.print("\033[31m");
                System.out.printf("%s\n", "No Matching Filesystems, file system types, or Mount Points.");
                System.out.print("\033[0m");
            } else {
                System.out.println(line(termType.getActualColumns(), termType,
                        termType.BOTTOM_LEFT_CORNER, termType.BOTTOM_RIGHT_CORNER));
            }
        } catch (Exception ex) {
            Logger.getLogger(DiskUsageTest.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Filter disks given optional file-systems types, optionally local only (not
     * networked), and whether to
     * include 'special' devices.
     * Additionally, a summary total line can be provided at the end.   
     * This version is called when no mount points provided on command-line
     * 
     * @param fsTypes
     * @param localFSOnly
     * @param fileString
     * @param color
     * @return number of entries
     * @throws IOException
     * 
     */
    private static int filterDisks(String header,
            TermType termType,
            List<String> fsTypes,
            List<MyFileSystem> fileSystems,
            boolean localFSOnly,
            boolean specialFSAlso,
            AnsiCodes.ANSI_COLOR color,
            boolean summary) 
    {
        return displaySpecifiedFileSystems(fileSystems, 
                                           header, termType, color, 
                                           fsTypes, 
                                           localFSOnly, specialFSAlso, 
                                           summary, true);        
    }

  
    /**
     * This version is called when a list of perspective mount
     * points are provided on command-line
     * @param header
     * @param termType
     * @param fsTypes
     * @param files
     * @param localFSOnly
     * @param specialFSAlso
     * @param color
     * @param summary
     * @param allFileSystems
     * @return number of entries
     */
      private static int filterDisks(String header,
            TermType termType,
            List<String> fsTypes,
            List<String> files,
            boolean localFSOnly,
            boolean specialFSAlso,
            AnsiCodes.ANSI_COLOR color,
            boolean summary, boolean allFileSystems) 
    {
        List<MyFileSystem> mfs = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            String fileString = files.get(i);
            if (debug) System.out.printf("Filtering on Perspective File System Mount Point '%s'\n", fileString);
            MyFileSystem returnedMFS = getJavaFileStore(termType, fileString);
            mfs.add(returnedMFS);
        }
        return displaySpecifiedFileSystems(mfs, 
                                           header, termType, color, 
                                           fsTypes, 
                                           localFSOnly, specialFSAlso, 
                                           summary, allFileSystems);
    }
    
      
    private static int displaySpecifiedFileSystems(List<MyFileSystem> mfs,
                                                   String header, TermType termType, AnsiCodes.ANSI_COLOR color,
                                                   List<String> fsTypes, 
                                                   boolean localFSOnly, boolean specialFSAlso, 
                                                   boolean summary, boolean allFileSystems)
        {
        List<MyFileSystem> filteredFileSystems = new ArrayList<>();        
        if (verbose) System.out.printf("\nFiltering retrieved results\n");
            for (MyFileSystem myFS : mfs) {
                filterFileSystemType(filteredFileSystems, myFS, fsTypes, localFSOnly, specialFSAlso, allFileSystems);
            }
            if (verbose) System.out.println();
            displayFileSystemList(filteredFileSystems, termType, header, color, summary);
            return filteredFileSystems.size();
        }
        
        private static void displayFileSystemList(List<MyFileSystem> filteredFileSystems,
            TermType termType,
            String header,
            AnsiCodes.ANSI_COLOR color, boolean summary) {
        if (!filteredFileSystems.isEmpty()) {
            System.out.println(line(termType.getActualColumns(),
                    termType,
                    termType.TOP_LEFT_CORNER, termType.TOP_RIGHT_CORNER));
            System.out.print(header);

            System.out.println(line(termType.getActualColumns(), termType, 
                    termType.LEFT_COLUMN_LINE,
                    termType.RIGHT_COLUMN_LINE));
            //if (myOS.getOS() == OS.OSTypes.WINDOWS && (!termType.isAnsiTerm())) {
            //    System.out.println();
            //}
            colorizeDiskUsage(filteredFileSystems, termType, color, summary);
        }
    }
    /*
     * Filter Java FileStorages based on above criteria
     */
    private static void filterFileSystemType(List<MyFileSystem> mfs,
            MyFileSystem mf,
            List<String> fsTypes,
            boolean localFSOnly,
            boolean specialFSAlso,
            boolean all) {
        
        if (verbose) System.out.printf("\tFiltering File System Types for: %-30.30s: ", mf.getName());
        
        // If all is specified without filesystem types, just
        // use the flags (special, local)
        if (all && ((fsTypes == null) || fsTypes.isEmpty())) {
            if (mf.isNetworkedDevice() && localFSOnly ) {
                if (verbose) System.out.printf("Skipping network device\n");
                return;
            }
            if (mf.isSpecialDevice() && !specialFSAlso)  {
                if (verbose && !debug) System.out.printf("Skipping Special Device");
                if (verbose) System.out.println();
                return;
            }
            if (all) {
                if (verbose) System.out.printf("Adding device '%s'\n", mf.getDevice());
                mfs.add(mf);
            }
        } else {
            if ((fsTypes == null) || fsTypes.isEmpty()) {
                if (verbose) System.out.printf("No Types specified, adding device '%s'\n", mf.getDevice());
                mfs.add(mf);
            } else {
                boolean found = false;
                for (String type : fsTypes) {
                    if (verbose) System.out.printf("Checking for Type '%s' => ", type);
                    if (mf.getType().startsWith(type)) {
                        if (verbose) System.out.printf("Found Match, adding device '%s'\n", mf.getDevice());
                        mfs.add(mf);
                        found = true;
                        break;
                    }
                }
                if (verbose && !found) System.out.printf("No Match\n");
            }
        }
    }

    /**
     * Output disk details in various formats, depending on operating system,
     * Terminal Type,
     * and terminal width.
     * 
     * @param myFS
     * @param termType
     * @return
     */
    private static String formatDiskUsage(MyFileSystem myFS, TermType termType) {
        String ret = "";
        if (myFS == null)
            return ret;
        if (myFS.getErrorMessage() != null || myFS.isBadFilesystem()) {
            String dev = myFS.getDevice();
            long len = termType.determineLineLength(termType.getActualColumns()) - 21;
            String fmt = "%-20.20s %s%s%-" + len + "." + len + "s";
            String errMsg = myFS.getErrorMessage().replaceAll("\\n", "").replaceAll("\\r", "");
            if (debug) System.out.printf("Error: fmt='%s', dev='%s', errMsg='%s'\n", fmt, dev, errMsg);
            return String.format(fmt,
                    dev,
                    AnsiCodes.getReset(termType),
                    AnsiCodes.ANSI_COLOR.RED.getBoldCode(termType),
                    errMsg);
        }
        ret = formatOutput(termType,
                myFS.getDevice(),
                myFS.getName(),
                myFS.getDiskUnit(),
                myFS.getPartition(),
                myFS.getTotalSpace(),
                myFS.getUsedSpace(),
                myFS.getUsableSpace(),
                myFS.getUsedPercentage(),
                myFS.getType(),
                myFS.isNetworkedDevice(),
                myFS.isSpecialDevice(),
                myFS.isNVMe());
        return ret;
    }

    /**
     * Helper method.
     * 
     * @param termType
     * @param device
     * @param name
     * @param unit
     * @param partition
     * @param totalSpace
     * @param usedSpace
     * @param freeSpace
     * @param usedPct
     * @param type
     * @param isNetworkedDevice
     * @param isSpecialDevice
     * @return
     */
    private static String formatOutput(TermType termType,
            String device,
            String name,
            String unit,
            String partition,
            String totalSpace,
            String usedSpace,
            String freeSpace,
            String usedPct,
            String type,
            boolean isNetworkedDevice,
            boolean isSpecialDevice,
            boolean isNVMe)

    {
        String ret;
        if (termType.getActualColumns() < termType.getMinColumnWidth()) {
            if (myOS.getOS() != OS.OSTypes.WINDOWS) {
                ret = String.format("%-20.20s %-19.19s %14.14s %14.14s %7.7s",
                        device, name, totalSpace, freeSpace, usedPct);
            } else {
                if (termType.isAnsiTerm()) {
                    ret = String.format("%-20.20s %-5.5s %14.14s %14.14s %7.7s",
                            device, name, totalSpace, freeSpace, usedPct);
                } else {
                    ret = String.format("%-20.20s %-5.5s %14.14s %14.14s",
                            device, name, totalSpace, freeSpace);
                }
            }
        } else if (myOS.getOS() != OS.OSTypes.WINDOWS) {
            String dskPart;
            if (isSpecialDevice) {
                dskPart = "<Special>";
            } else {
                if (isNetworkedDevice) {
                    dskPart = "<Net>";
                } else {
                    if (isNVMe) {
                        dskPart = String.format("%-4s %3s",
                                unit,
                                partition);
                    } else {
                        dskPart = String.format("%-4s %4s",
                                (!unit.isEmpty() ? unit : "- - "),
                                (!partition.isEmpty() ? partition : "- - "));
                    }
                }
            }
            ret = String.format("%-20.20s %-36.36s %-9s %14.14s %14.14s %14.14s %7.7s %12.12s",
                    device, name,
                    dskPart,
                    totalSpace, usedSpace, freeSpace, usedPct,
                    type);
        } else {
            ret = String.format("%-20.20s %-5.5s %14.14s %14.14s %14.14s %7.7s %12.12s",
                    device, name,
                    totalSpace, usedSpace, freeSpace, usedPct,
                    type);
        }

        return ret;
    }

    /**
     * When No Arguments provided, get all of the known mounted filesystems.
     * 
     * @param termType
     * @return
     * @throws IOException
     */
    private static List<MyFileSystem> getAllFilesystemRootFromJava(TermType termType)
            throws IOException, Exception {
        int numSystemStores = 0;
        String intro = "Getting FileSystem Roots =>  ";
        if (!verbose) System.out.printf("%s", intro);
        List<MyFileSystem> fileSystemRoots = new ArrayList<>();
        FileStore store;
        FileSystem rootfs = FileSystems.getDefault();
        int col = intro.length() + 1;
        Iterator<FileStore> it = rootfs.getFileStores().iterator();
        if (!verbose) {
            System.out.printf("%s", 
                    AnsiCodes.ANSI_COLOR.GREY.getReverseHighlightCode(termType));
        }
        Date start = new Date();
        while (it.hasNext()) {
            numSystemStores++;
            store = it.next();
            if (!verbose) {
                col++;                
                if (col % 2 == 0) System.out.printf("\b /"); else System.out.printf("\b \\");
                System.out.flush();                
            }
            MyFileSystem mfs = new MyFileSystem(termType, myOS, store, m_kb, verbose, debug);
            fileSystemRoots.add(mfs);
            if (!verbose) {
                if (col >= 80) {
                    System.out.printf("%s", AnsiCodes.getReset(termType));
                    for (int i = 1; i < 80 - (intro.length()); i++) System.out.printf("\b");
                    for (int i = 1; i < 80 - (intro.length()); i++) System.out.printf(" ");
                    for (int i = 1; i < 80 - (intro.length()); i++) System.out.printf("\b");                
                    col = intro.length() + 1;
                    System.out.printf("%s", 
                            AnsiCodes.ANSI_COLOR.GREY.getReverseHighlightCode(termType));
                }
            }
        }
        Date end = new Date();
        long timeSecs = (end.getTime() - start.getTime()) / 1000;
        
        if (!verbose) {
            System.out.printf("%s", AnsiCodes.getReset(termType));
            System.out.printf("\r");
            for (int i = 1; i < 80; i++) System.out.printf(" ");
            System.out.printf("\r");
            if (timeSecs > 0) {
                System.out.printf("%s%s%s [%s%d seconds%s]\n", 
                        AnsiCodes.ANSI_COLOR.GREEN.getBoldCode(termType),
                        "Done Getting FileSystem Roots",
                        AnsiCodes.getReset(termType),
                        AnsiCodes.ANSI_COLOR.GREY.getHighlightCode(termType),                    
                        timeSecs,
                        AnsiCodes.getReset(termType));
            }
            
        }
        if (debug) System.out.printf("Number of filestores Reported by Operating System named %s: %d\n", myOS.getOS().name(), numSystemStores);
        return fileSystemRoots;
    }

    /**
     * Intelligently draw a separator line
     * 
     * @param length
     * @param termType
     * @return
     */
    private static String line(long length, TermType termType) {
        return line(length, termType, termType.LEFT_COLUMN_LINE, termType.RIGHT_COLUMN_LINE);
    }

    /**
     * Use provided line drawing characters.
     * 
     * @param passedLength
     * @param termType
     * @param ld
     * @param rd
     * @param colorOn
     * @return
     */
    private static String line(long passedLength, TermType termType, String ld, String rd) {
        StringBuilder l = new StringBuilder();
        long length = termType.determineLineLength(passedLength);
        String space = termType.ANSI_LINE;
        l.append(AnsiCodes.getReset(termType));
        l.append(ld);
        if (debug) System.out.printf("Bulding line, passedLength = %d, length=%d\n", passedLength, length);
        for (int i = 0; i < length; i++) {
            l.append(space);
        }
        l.append(AnsiCodes.getReset(termType));
        l.append(rd);
        return l.toString();
    }

 

    /**
     * Parse Command Line Arguments
     * 
     * @param args
     * @param fsTypes
     * @param fsLocations
     * @return
     */
    private static boolean[] parseCommandLineArgs(String[] args,
            List<String> fsTypes,
            List<String> fsLocations) {
        boolean[] flags = { false, false, false };
        String command = DiskUsageTest.class.getSimpleName();
        int argc = 0;
        while (argc < args.length) {
            if (args[argc].startsWith("-t") || args[argc].startsWith("--ty")) {
                argc++;
                if (argc > (args.length - 1)) {
                    usage(command, args);
                }
                if (args[argc].startsWith("-") || args[argc].startsWith("+")) {
                    usage(command, args);
                }
                String argVal = args[argc];
                String[] fs = argVal.split(",");
                if (fs.length == 0) {
                    usage(command, args);
                }
                // Unique list of filesystem types
                SortedSet<String> s = new TreeSet<>();
                Collections.addAll(s, fs);
                fsTypes.addAll(s);

                // By definition, providing a filesystem type turns on 'special' devices
                flags[1] = true;

                argc++;

            } else if (args[argc].startsWith("-l") || args[argc].startsWith("--loc")) {
                flags[0] = true;
                argc++;
            } else if (args[argc].startsWith("+s") || args[argc].startsWith("--spe")) {
                flags[1] = true;
                argc++;
            } else if (args[argc].startsWith("-H") || args[argc].startsWith("--si")) {
                m_kb = 1000.0;
                argc++;
            } else if (args[argc].startsWith("-h") || args[argc].startsWith("--hum")) {
                m_kb = 1000.0;
                argc++;
            }

            else if (args[argc].startsWith("-s") || args[argc].startsWith("--su")) {
                flags[2] = true;
                argc++;
            } else if (args[argc].startsWith("-d") || args[argc].startsWith("--deb")) {
                System.out.printf("Running with detailed debugging\n");
                debug = true;
                verbose = true;
                argc++;
            } else if (args[argc].startsWith("-v") || args[argc].startsWith("--verb")) {
                System.out.printf("Running in verbose mode\n");
                verbose = true;
                argc++;
            } else if (args[argc].startsWith("-") || args[argc].startsWith("+")) {
                usage(command, args);
            } else {
                while ((argc < args.length) && !args[argc].startsWith("-") && !args[argc].startsWith("+")) {
                    fsLocations.add(args[argc++]);
                }
            }
        }
        return flags;
    }

    /**
     * Provide Command-line Usage Instructions.
     * 
     * @param command
     * @param args
     */
    private static void usage(String command, String[] args) {
        System.out.println();
        System.out.println("Fancy Disk Usage Reporting Tool:");
        System.out.printf(
                "\t%s: [-v | -d] [-s] [-l] [+s] [-t <type1>[,<type2>[,...[,<typeN>]]]] [mountPoint1 [mountPoint2 [... mountPointN]]]\n",
                command);
        System.out.println();
        System.out.printf("\t\t-s, --summary : Provide summary line\n");
        System.out.printf("\t\t-l,   --local : local filesystems only\n");
        System.out.printf("\t\t+s, --special : Include 'Special' filesystems\n");
        System.out.printf("\t\t-t,    --type :  Optional list of filesystem types\n");
        System.out.printf("\t\t-h,   --human : Human Readable: Print Sizes in Powers of 1024\n");
        System.out.printf("\t\t-H,      --si : Human Readable: Print Sizes in Powers of 1000\n");
        System.out.printf("\t\t-d,   --debug : Debug Mode\n");
        System.out.printf("\t\t-v, --verbose : Verbose Mode\n");
        System.out.println();
        System.out.printf(
                "\tAny Arguments without a flag indicate a mount point to use to filter filesystem report on\n");
        System.out.println();
        System.out.println();
        System.exit(1);
    }
}
