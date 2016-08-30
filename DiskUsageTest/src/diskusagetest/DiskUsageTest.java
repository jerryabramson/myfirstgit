/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package diskusagetest;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jerry
 */
public class DiskUsageTest {

    public static final String os = System.getProperty("os.name");
    public static String spaces = "                                                                                                               ";

    public static long cols = 80;
    public static OS myOS;
    public static long minCols;
    public static boolean ansi = true;
    public static StringBuffer globalLine = null;
    public static String space = " ";

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args)
    {

        switch (os.toLowerCase().substring(0, 3))
        {
            case "mac":
                myOS = OS.MAC;
                break;
            case "lin":
                myOS = OS.LINUX;
                break;
            case "win":
                myOS = OS.WINDOWS;
                break;
            default:
                myOS = OS.UNKNOWN;
                break;
        }
        List<String> fsTypes = new ArrayList<>();
        System.out.println();

        String colEnv = System.getenv("COLUMNS");
        cols = 80;
        minCols = 172;
        try
        {
            cols = Long.valueOf(colEnv);
        }
        catch (NumberFormatException e)
        {
            cols = 80;
        }
        if (myOS == OS.WINDOWS)
        {
            minCols = 112;
            ansi = (System.getenv("TERM") != null);
            if (!ansi)
            {
                cols = 200;
                space = "-";
            }

        }
        if (cols < minCols)
        {
            if (myOS != OS.WINDOWS)
            {
                System.out.format("\033[31m%-20.20s %-21.21s %14.14s %14.14s %7.7s\033[0m\n",
                                  "Device", "Mount Point", "Total", "Avail", "Used %");
            }
            else if (ansi)
            {
                System.out.format("\033[31m%-20.20s %-5.5s %14.14s %14.14s %7.7s\033[0m\n",
                                  "Device", "Mount Point", "Total", "Avail", "Used %");
            }
            else
            {
                System.out.format("%-20.20s %-5.5s %14.14s %14.14s\n",
                                  "Device", "Mount Point", "Total", "Avail");
            }
            line(0, 80);
        }
        else if (myOS != OS.WINDOWS)
        {
            System.out.format("\033[31;1m%-40.40s %-55.55s %4s %4s %14.14s %14.14s %14.14s %7.7s %12.12s\033[0m\n",
                              "Device", "Mount Point", "Disk", "Part", "Total", "Used", "Avail", "Used %", "Type");
            line(0, minCols);
        }
        else
        {
            if (ansi)
            {
                System.out.format("\033[31m%-40.40s %-5.5s %14.14s %14.14s %14.14s %7.7s %12.12s\033[0m\n",
                                  "Device", "Mount Point", "Total", "Used", "Avail", "Used %", "Type");
                System.out.print("\033[31m");
            }
            else
            {
                System.out.format("%-40.40s %-5.5s %14.14s %14.14s %14.14s %7.7s %12.12s\n",
                                  "Device", "Mount Point", "Total", "Used", "Avail", "Used %", "Type");
            }
            line(0, minCols);
        }

//        System.out.format("%-20.20s %50.50s %4s %4s %10.10s %10.10s %1.1s %15.15s\n", 
//                        "------------", "--------------", "----", "----", "---------", "--------", "--------", "------");        
        int argc = 0;
        try
        {
            if (args.length > argc)
            {
                if (args[argc].startsWith("-t") || args[argc].startsWith("--t"))
                {
                    String argVal = args[++argc];
                    String[] fs = argVal.split(",");
                    if (fs == null)
                    {
                        fsTypes.add(argVal);
                    }
                    else
                    {
                        fsTypes = Arrays.asList(fs);
                    }
                    argc++;
                }
            }

            int color = 30;
            if (argc == (args.length))
            {
                diskUsage(fsTypes, color);
            }
            else
            {
                List<MyFileSystem> fileSystems = new ArrayList<>();
                for (; argc < args.length; argc++)
                {
                    MyFileSystem fs = diskUsage(fsTypes, args[argc], color++);
                    if (fs != null)
                    {
                        fileSystems.add(fs);
                    }
                }
                diskUsage(fsTypes, fileSystems, color);
            }
        }
        catch (IOException ex)
        {
            ex.printStackTrace(System.err);
        }
        if (ansi)
        {
            System.out.print("\033[0m");
        }
    }

    private static void diskUsage(List<String> fsTypes,
                                  List<MyFileSystem> myFileSystems,
                                  int color) throws IOException
    {

        Collections.sort(myFileSystems);
        String lastDiskUnit = null;
        String lastDiskType = null;
        boolean lastSpecial = false;
        for (MyFileSystem myFS : myFileSystems)
        {
            String out = formatDiskUsage(fsTypes, myFS, color);
            String unit = myFS.getDiskUnit();
            String type = myFS.getType();

            if (out != null)
            {
                if ((lastDiskType != null) && 
                        (!lastDiskType.equals(type) || 
                        ((unit == null) && (lastDiskUnit != null)) ||
                        ((lastDiskUnit == null) && (unit != null)) ||
                        (!lastDiskUnit.equals(unit))))
                {
                    if (!"ext".equals(type.substring(0, 3)) || !"ext".equals(lastDiskType.substring(0, 3)))
                    {
                        if (!lastSpecial && (!lastDiskUnit.equals(unit)))
                        {
                            line(color, out.length() - 1);
                        }
                        lastSpecial = myFS.isSpecial();
                        color++;
                        if (color > 46)
                        {
                            color = 30;
                        }
                        else if (color > 36 && color < 40)
                        {
                            color = 40;
                        }
                        if (color == 33 || color == 43)
                        {
                            color++;
                        }
                    }
                }
                lastDiskUnit = unit;
                lastDiskType = type;

            }
            if (out != null)
            {
                if (ansi)
                {
                    if (color >= 40)
                    {
                        int c = color - 10;
                        System.out.printf("\033[%d;%dm", c, 1);
                    }
                    else
                    {
                        System.out.printf("\033[%dm", color);
                    }
                }
                System.out.printf("%s", out);
            }
            else
            {
                lastDiskUnit = unit;
            }
        }
    }

    private static void diskUsage(List<String> fsTypes,
                                  Iterable<FileStore> fs,
                                  int color)
            throws IOException
    {
        List<MyFileSystem> myFileSystems = new ArrayList<>();
        FileStore store;
        Iterator<FileStore> it = fs.iterator();
        while (it.hasNext())
        {
            store = it.next();
            myFileSystems.add(new MyFileSystem(store));
        }
        diskUsage(fsTypes, myFileSystems, color);
    }

    public static void line(int color,
                            long length)
    {

        if (cols < minCols)
        {
            length = 80;
            if (myOS == OS.WINDOWS)
            {
                length = 56;
            }
        }
        if (globalLine == null)
        {
            globalLine = new StringBuffer(120);
            space = " ";
            if (!ansi)
            {
                space = "-";
            }
            for (int i = 0; i < length; i++)
            {
                globalLine.append(space);
            }

        }
        
        String fmt = "%" + length + "." + length + "s";
        if (ansi)
        {
            System.out.printf("\033[4m");
        }
        System.out.printf(fmt + "\n", globalLine);
        if (ansi)
        {
            System.out.printf("\033[0m");
        }
    }

    private static void diskUsage(List<String> fsTypes,
                                  int color)
            throws IOException
    {
        diskUsage(fsTypes, FileSystems.getDefault().getFileStores(), color);
    }

    private static String formatDiskUsage(List<String> fsTypes,
                                          MyFileSystem myFS,
                                          int color)
            throws IOException
    {
        String ret = null;

        if ((fsTypes == null) || fsTypes.isEmpty() || fsTypes.contains(myFS.getType()))
        {
            String total = myFS.getTotalSpace();
            String used = myFS.getUsedSpace();
            String avail = myFS.getUsableSpace();
            String usedPct = myFS.getUsedPercentage();

            if (cols < minCols)
            {
                if (myOS != OS.WINDOWS)
                {
                    ret = String.format("%-20.20s %-21.21s %14.14s %14.14s %7.7s\n",
                                        myFS.getDevice(), myFS.getName(),
                                        total, avail, usedPct);
                }
                else
                {
                    ret = String.format("%-20.20s %-5.5s %14.14s %14.14s %7.7s\n",
                                        myFS.getDevice(), myFS.getName(),
                                        total, avail, usedPct);
                }
            }
            else if (myOS != OS.WINDOWS)
            {
                ret = String.format("%-40s %-55s %-4s %-4s %14.14s %14.14s %14.14s %7.7s %12.12s\n",
                                    myFS.getDevice(), myFS.getName(),
                                    (!myFS.getDiskUnit().isEmpty() ? myFS.getDiskUnit() : "-"), 
                                    (!myFS.getPartition().isEmpty() ? myFS.getPartition() : "--"),
                                    total, used, avail, usedPct,
                                    myFS.getType());
            }
            else
            {
                ret = String.format("%-40.40s %-5.5s %14.14s %14.14s %14.14s %7.7s %12.12s\n",
                                    myFS.getDevice(), myFS.getName(),
                                    total, used, avail, usedPct,
                                    myFS.getType());
            }
        }
        return ret;
    }

    /**
     *
     * @param fileString
     * @param color
     *
     * @throws IOException
     */
    public static MyFileSystem diskUsage(List<String> fsTypes,
                                         String fileString,
                                         int color)
    {
        MyFileSystem ret = null;
        try
        {
            Path path = Paths.get(fileString).toAbsolutePath().normalize();
            //  System.out.println("path = '" + path.toString() + "'");
            FileSystem fs = path.getFileSystem();
            FileStore store = Files.getFileStore(path);
            ret = new MyFileSystem(store);
        }
        catch (FileSystemException ex)
        {
            System.err.println("\033[33mNo Valid Filesystem for " + fileString + ": " + ex + "\033[0m");
        }
        catch (IOException ex)
        {
            System.err.println("Unknown exception for " + fileString + ": " + ex);
            ex.printStackTrace(System.err);
        }
        return ret;
    }

    public enum OS {
        LINUX,
        MAC,
        WINDOWS,
        UNKNOWN
    }

}

class MyFileSystem implements Comparable<MyFileSystem> {

    String m_device;
    String m_diskUnit = "";
    String m_name;
    String m_partition = "";
    long m_totalSpace;
    String m_type;
    long m_unallocatedSpace;
    long m_usableSpace;

    public MyFileSystem(FileStore store)
            throws IOException
    {

        m_totalSpace = store.getTotalSpace();
        m_unallocatedSpace = store.getUnallocatedSpace();
        m_usableSpace = store.getUsableSpace();
        m_device = store.name().trim();

        m_name = store.toString().replaceFirst(m_device, "").trim();
        m_name = m_name.replaceFirst("\\(\\)", "").trim();
        m_type = store.type().trim();
        File deviceFile;
        if (m_device.endsWith(File.separator))
        {
            String devShort = m_device.substring(0, m_device.length() - 1);
            deviceFile = new File(devShort);
        }
        else
        {
            deviceFile = new File(m_device);
        }
        String dev = deviceFile.getName().trim();
        m_diskUnit = "";
        m_partition = "";
        String dsk = dev;
        if (m_usableSpace == 0)
        {
            m_diskUnit = "";
            m_partition = "";
            //m_name = store.toString();
        }
        else if (m_device.startsWith("//"))
        {
            m_diskUnit = "";
            m_partition = "";
        }
        else
        {
            switch (DiskUsageTest.myOS)
            {
                case MAC:
                    if (!m_type.equalsIgnoreCase("nfs") && !m_type.equalsIgnoreCase("smbfs"))
                    {
                        if (dev.startsWith("disk"))
                        {
                            dsk = dev.replaceFirst("disk", "").trim();
                        }
                        m_diskUnit = dsk.substring(0, 1);
                        m_partition = dev.replaceFirst("disk" + m_diskUnit, "").trim();
                    }
                    break;
                case LINUX:
                    if (dev.startsWith("sd") || dev.startsWith("md"))
                    {
                        m_diskUnit = dev.substring(2, 3);
                        m_partition = dev.substring(3);
                    }
                    else if (dev.startsWith("xvd"))
                    {
                        m_diskUnit = dev.substring(3, 4);
                        m_partition = dev.substring(4);
                    }
                    break;
                case WINDOWS:
                    break;
            }
        }
    }

    public boolean isSpecial()
    {
        return (m_totalSpace == 0);
    }

    private String normalizeValue(long val)
    {
        if (val == 0)
        {
            return String.format("%14.14s", "---");
        }
        double k = 1024.0;
        double m = k * k;
        double g = m * k;
        double gB = Double.valueOf(val) / g;
        if (gB > 1.0)
        {
            return String.format("%5.2f GiB", gB);
        }
        double mB = Double.valueOf(val) / m;
        if (mB > 1.0)
        {
            return String.format("%5.2f MiB", mB);
        }
        double kB = Double.valueOf(val) / k;
        if (kB > 1.0)
        {
            return String.format("%5.2f KiB", kB);
        }
        return String.format("%8d B", val);
    }

    @Override
    public int compareTo(MyFileSystem fs)
    {
        int i = 0;
        if ((m_totalSpace == 0) && (fs.m_totalSpace != 0))
        {
            i = 1;
        }
        else if ((m_totalSpace > 0) && (fs.m_totalSpace == 0))
        {
            i = -1;
        }
        else if ((getDiskUnit().length() == 0) && (fs.getDiskUnit().length() > 0))
        {
            i = 1;
        }
        else if ((getDiskUnit().length() > 0) && (fs.getDiskUnit().length() == 0))
        {
            i = -1;
        }
        else if ((getDiskUnit().length() == 0) && (fs.getDiskUnit().length() == 0))
        {
            try
            {
                i = getDevice().compareTo(fs.getDevice());
            }
            catch (IOException ex)
            {
                i = 0;
            }
        }
        else if ((getDiskUnit().length() > 0) && (fs.getDiskUnit().length() > 0))
        {
            i = getDiskUnit().compareTo(fs.getDiskUnit());
            if (i == 0)
            {
                if ((getPartition().length() == 0) && (fs.getPartition().length() > 0))
                {
                    i = 1;
                }
                if ((getPartition().length() > 0) && (fs.getPartition().length() == 0))
                {
                    i = -1;
                }
                else
                {
                    i = getPartition().compareTo(fs.getPartition());
                }
            }
        }
        else
        {
            i = m_type.compareTo(fs.m_type);
        }
        return i;
    }

    public String getDevice() throws IOException
    {
        String bd = m_device;
        if (m_type.equalsIgnoreCase("nfs"))
        {

            String[] nfs = m_device.split(":");
            if (nfs.length == 2)
            {
                String host = nfs[0];
                File mt = new File(nfs[1]);
                String base = mt.getName();
                String[] comps = mt.getCanonicalPath().split(File.separator);
                String first = "";
                if (comps.length > 0)
                {
                    first = comps[0];
                }
                bd = String.format("%s:/%s.../%s", host, first, base);
            }
        }
        else if (m_type.equalsIgnoreCase("smbfs"))
        {
            String[] smbfs = m_device.split("/");
            if (smbfs.length == 4)
            {
                String conn = smbfs[2];
                String[] cstr = conn.split(":");
                if (cstr.length == 3)
                {
                    conn = String.format("%.7s:%.12s...", cstr[0], cstr[1]);
                }
                String dev = smbfs[3];
                String d = URLDecoder.decode(dev);
                bd = String.format("//%s/%17.17s", conn, d);
            }
        }
        return bd;
    }

    public void setDevice(String device)
    {
        this.m_device = device;
    }

    public String getDiskUnit()
    {
        return m_diskUnit;
    }

    public void setDiskUnit(String diskUnit)
    {
        this.m_diskUnit = diskUnit;
    }

    public String getName()
    {
        return m_name;
    }

    public void setName(String name)
    {
        this.m_name = name;
    }

    public String getPartition()
    {
        return m_partition;
    }

    public void setPartition(String partition)
    {
        this.m_partition = partition;
    }

    public String getTotalSpace()
    {
        return normalizeValue(m_totalSpace);
    }

    public void setTotalSpace(long totalSpace)
    {
        this.m_totalSpace = totalSpace;
    }

    public String getType()
    {
        return m_type;
    }

    public void setType(String type)
    {
        this.m_type = type;
    }

    public String getUnallocatedSpace()
    {
        return normalizeValue(m_unallocatedSpace);
    }

    public void setUnallocatedSpace(long unallocatedSpace)
    {
        this.m_unallocatedSpace = unallocatedSpace;
    }

    public String getUsableSpace()
    {
        return normalizeValue(m_usableSpace);
    }

    public String getUsedSpace()
    {
        return normalizeValue(m_totalSpace - m_unallocatedSpace);
    }

    public void setUsableSpace(long usableSpace)
    {
        this.m_usableSpace = usableSpace;
    }

    String getUsedPercentage()
    {
        if (m_totalSpace == 0)
        {
            return String.format("%3.3s", "---");
        }
        double diff = (m_totalSpace - m_unallocatedSpace);
        double pct = diff / (double) m_totalSpace * (double) 100.0;
        return String.format("%5.2f%%", pct);
    }

}
