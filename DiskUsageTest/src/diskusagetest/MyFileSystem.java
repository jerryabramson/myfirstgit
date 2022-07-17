/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package diskusagetest;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.file.FileStore;
import java.util.regex.Matcher;
import java.nio.file.FileSystemException;

/**
 *
 * @author jabramso
 */
class MyFileSystem implements Comparable<MyFileSystem>
{
    boolean m_verbose = false;
    boolean m_debug = false;
    String m_device = "<Unknown>";
    String m_diskUnit = "";
    String m_name = "<Unknown>";
    String m_partition = "";
    long m_totalSpace;
    String m_type = "<Unknown>";
    long m_unallocatedSpace;
    long m_usableSpace;
    String m_errorMessage = null;
    boolean m_logicalDisk = false;
    TermType m_termType;
    OS m_myOS;
    double m_kb = 1024.0;
    
    boolean m_logicalVolume = false;
    boolean m_raidDisk = false;
    boolean m_badFilesystem = false;
    boolean m_nvme = false;
    boolean m_wholeDisk = false;
    boolean m_cdRom = false;

    public void setwholeDisk(boolean m_wholeDisk) { this.m_wholeDisk = m_wholeDisk; }
    public void setVerbose(boolean v) { m_verbose = v; }
    public void setDebug(boolean d) { m_debug = d; }
    public void setM_cdRom(boolean m_cdRom) { this.m_cdRom = m_cdRom; }
    public boolean isCDRom() { return m_cdRom; }
    public boolean isWholeDisk() { return m_wholeDisk; }
    public boolean isBadFilesystem() { return m_badFilesystem; }
    
    public void set_badFilesystem(TermType termType, OS os, boolean badFilesystem)
    {
        m_badFilesystem = badFilesystem;
        m_myOS = os;
    }
    

    public MyFileSystem(TermType termType, OS os, String errorMessage, double kSize, boolean verbose, boolean debug)
    {
        m_errorMessage = errorMessage;
        m_type = "";
        m_name = "";
        m_device = "";
        m_badFilesystem = true;
        m_termType = termType;
        m_myOS = os;
        m_kb = kSize;
        m_verbose = verbose;
        m_debug = debug;
    }

    public MyFileSystem(TermType termType, OS os, FileStore store, double kSize, boolean verbose, boolean debug)
            throws IOException
    {
        m_errorMessage = null;
        m_myOS = os;
        m_kb = kSize;
        m_totalSpace = 0; 
        m_unallocatedSpace = 0;
        m_usableSpace = 0;
        m_debug = debug;
        m_verbose = verbose;
        try {
            if (m_verbose) System.out.printf("Store Named: '%s', ", store.toString());
            m_device = store.name().trim();
        } catch (Exception e) {
            m_errorMessage = String.format("Cannot Process Entry: %s", e);
            if (m_verbose) { System.out.println(m_errorMessage); }
            if (m_debug) { e.printStackTrace(System.out); }
            m_badFilesystem = true;
            m_totalSpace = -1;
            m_unallocatedSpace = -1;
            m_usableSpace = -1;
            m_type = "<unknown>";
            m_device = m_type;
            return;
        }
        try {
            m_totalSpace = store.getTotalSpace();
            m_unallocatedSpace = store.getUnallocatedSpace();
            m_usableSpace = store.getUsableSpace();
        } catch (java.nio.file.AccessDeniedException ne) {
            m_errorMessage = String.format("invalid: %s", ne);
            if (m_verbose) { System.out.println(m_errorMessage); }
            if (m_debug) { ne.printStackTrace(System.out); }
            m_badFilesystem = true;
            m_totalSpace = -1;
            m_unallocatedSpace = -1;
            m_usableSpace = -1;
            m_type = "<unknown>";
        } catch (FileSystemException e) {
            m_errorMessage = String.format("Error: %s", e);
            if (m_verbose) { System.out.println(m_errorMessage); }
            if (m_debug) { e.printStackTrace(System.out); }
            m_badFilesystem = true;
            m_totalSpace = -1;
            m_unallocatedSpace = -1;
            m_usableSpace = -1;
            m_type = "<unknown>";
        }


        if (m_debug) System.out.printf("usableSpace = %d (%f GiB), ", m_usableSpace, (double) m_usableSpace / 1024.0 / 1024.0 / 1024.0);
        if ((m_totalSpace == 0) && ((m_unallocatedSpace > 0) || (m_usableSpace > 0))) {
            m_totalSpace = -1;
        }
        m_device = store.name().trim();
        String fakeDevice = m_device;
        File deviceFile;
        try
        {
            if (m_debug) System.out.printf("store.name() = '%s', ", store.name());
            if (store.name().startsWith("/"))
            {
                File realFile = new File(store.name());
                m_device = realFile.getCanonicalPath();
                if (m_debug) System.out.print("real device '" + m_device + "', ");
            }
        }
        catch (java.nio.file.NoSuchFileException nf)
        {
            System.err.println("Not a real file: " + store.name());
            m_device = store.name();
        }
        String stName = Matcher.quoteReplacement(store.name());
       // String quoteName = Matcher.quoteReplacement(store.toString());
        String strippedName = store.toString().replaceAll(stName, "").trim();
        m_name = strippedName.replaceFirst("\\(\\)", "").trim();
    
        if ((m_myOS.getOS() == OS.OSTypes.WINDOWS) && m_name.startsWith("(") && m_name.endsWith(")"))
        {
            m_name = m_name.substring(1, m_name.length() - 1);
            if (m_debug) System.out.printf("Drive Letter on Windows: '%s', ", m_name);            
        }

        m_type = store.type().trim();
        if (m_device.endsWith(File.separator)) {
            String devShort = m_device.substring(0, m_device.length() - 1);
            deviceFile = new File(devShort);
        } else {
            deviceFile = new File(m_device);
        }

        String dev = deviceFile.getName().trim();
        m_diskUnit = "";        m_partition = "";        String dsk = dev;
        if (m_totalSpace == 0)   {
            m_diskUnit = "";
            m_partition = "";
            //m_name = store.toString();
        } else if (m_device.startsWith("//")) {
            m_diskUnit = "";
            m_partition = "";
        } else {
            determineUnitAndPartition(dev, dsk, fakeDevice, deviceFile);
            
        }
        if (m_verbose) System.out.println();
    }

   
    
    public boolean isNonStorageDevice()
    {
        return (m_totalSpace == 0);
    }

    protected static String normalizeValue(long v, double kb) throws Exception
    {
        
        if (v == -1) return "Unknown";
//        if (v == -1) return "X X X X";
//        if (v < 0) throw new Exception( "Value less than 0: " + v);
        if (v == 0)
        {
            return String.format("%14.14s", "- - - -");
        }
        double val = (double) v;
        return normalizeValue(val, kb);
    }
    
    protected static String normalizeValue(double val, double kb) throws Exception
    {
//        if (val == -1) return "X X X X";
        if (val == -1) return "Unknown";

        if (val < 0) throw new Exception( "Value less than 0: " + val);
        if (val == 0)
        {
            return String.format("%14.14s", "- - - -");
        }
        double k = kb;
        String ebString = "EiB";
        String pbString = "PiB";
        String tbString = "TiB";
        String gbString = "GiB";
        String mbString = "MiB";
        String kbString = "KiB";
        if (k == 1000.0)
        {
            ebString = "EB";
            pbString = "PB";
            tbString = "TB";
            gbString = "GB";
            mbString = "MB";
            kbString = "KB";
        }
        double m = k * k;
        double g = m * k;
        double t = g * k;
        double p = t * k;
        double e = p * k;

        double eB = val / e;
        if (eB > 1.0) {
            return String.format("%5.2f %3.3s", eB, ebString);
        }

        double pB = val / p;
        if (pB > 1.0)
        {
            return String.format("%5.2f %3.3s", pB, pbString);
        }
        double tB = val / t;
        if (tB > 1.0)
        {
            return String.format("%5.2f %3.3s", tB, tbString);
        }
        double gB = val / g;
        if (gB > 1.0)
        {
            return String.format("%5.2f %3.3s", gB, gbString);
        }
        double mB = val / m;
        if (mB > 1.0)
        {
            return String.format("%5.2f %3.3s", mB, mbString);
        }
        double kB = val / k;
        if (kB > 1.0)
        {
            return String.format("%5.2f %3.3s", kB, kbString);
        }
        return String.format("%8.0f B", val);
    }

    @Override
    public int compareTo(MyFileSystem fs)
    {
        int ret = 0;
        if (DiskUsageTest.myOS.getOS() == OS.OSTypes.WINDOWS)
        {
            // Windows uses simple Drive letters, with no partition numbers, so a simple comparision is enough
            return getName().compareTo(fs.getName());
        }

        // Bad Filesystems always come last
        if (isBadFilesystem()) { return 1; }
        
        // NVMe Come first
        if (m_nvme && fs.isNVMe()) {
            Integer partNumber = Integer.valueOf(getPartition().substring(1));
            Integer comparePartNumber = Integer.valueOf(fs.getPartition().substring(1));
            Integer driveUnitOrder = compareDriveUnits(getDiskUnit(), fs.getDiskUnit());
            if (m_verbose) {
                System.out.printf("      Comparing: %14.14s <=> %14.14s\n", fs.getDevice(), getDevice());
                System.out.printf("     partNumber: %d <=> %d\n", partNumber, comparePartNumber);
                System.out.printf(" driveUnitOrder: %d\n", driveUnitOrder);
            }
            if (driveUnitOrder == 0) {
                return partNumber.compareTo(comparePartNumber);
            } else {
                return driveUnitOrder;
            }
        } else if (m_nvme && !fs.isNVMe()) {
            return -1;
        } else if (!m_nvme && fs.isNVMe()) {
            return 1;
        } else if (fs.isLogicalDisk()) {
            return -1;
        }
        
        // Logical Disks always come before anything else, including before raid
        if (m_logicalDisk)
        {
            if (fs.isLogicalDisk())
            {
                return getName().compareTo(fs.getName());
            }
            if (fs.isRaidDisk())
            {
                return -1;
            }
            if (fs.isNVMe()) {
                return -1;
            }
        }
        
        
        
        // Raid disks are sorted by partition number, if the compared disk is also raid.
        // Otherwise, raid comes before real, and after logical
        if (m_raidDisk)
        {
            if (fs.isLogicalDisk())
            {
                return 1;
            }
            if (fs.isNVMe()) {
                return 1;
            }
            if (fs.isRaidDisk())
            {
                try
                {
                    Integer partNumber = Integer.valueOf(getPartition().substring(1));
                    Integer comparePartNumber = Integer.valueOf(fs.getPartition().substring(1));
                    if (m_debug) { 
                        System.out.printf("partition Number = %d, comapred to %d\n",
                                          partNumber,
                                          comparePartNumber);
                    }
                    return partNumber.compareTo(comparePartNumber);
                }
                catch (NumberFormatException ne)
                {
                    return -1; 
                }
            }

            // Raid disks are before regular devices
            return -1;
        }
        
        // Special devices come after everything
        if (isSpecialDevice() && !fs.isSpecialDevice())
        {
            return 1;
        }
        if (!isSpecialDevice() && fs.isSpecialDevice())
        {
            return -1;
        }
        
        
        // Sort special devices as follows:
        //   - Special devices that report a total size
        //        - followed by special devices without a storage size
        if ((m_totalSpace == 0) && (fs.m_totalSpace != 0))
        {
            // 'special' devices without storage always come at the end
            return 1;
        }
        
        if ((m_totalSpace > 0) && (fs.m_totalSpace == 0))
        {
            // Special devices with storage come before special devices without storage
            return -1;
        }

        if ((m_totalSpace == 0) && (fs.m_totalSpace == 0)) {
            return getDevice().compareTo(fs.getDevice());
        }
        
        if ((getDiskUnit().length() == 0) && (fs.getDiskUnit().length() > 0))
        {
            // Non drive units always come after everything
            return 1;
        }

        if ((getDiskUnit().length() > 0) && (fs.getDiskUnit().length() == 0))
        {
            // drive units come before non-drive units
            return -1;
        }

        if ((getDiskUnit().length() == 0) && (fs.getDiskUnit().length() == 0))
        {
            // If no partitions, simple comparison
            return getDevice().compareTo(fs.getDevice());
        }
        
        // Ok, we are now sorting physical disks with partitions
        if ((getDiskUnit().length() > 0) && (fs.getDiskUnit().length() > 0))
        {
            ret = compareDriveUnits(getDiskUnit(), fs.getDiskUnit());
            if (ret == 0)
            {
                if (getDiskUnit().equals("nvme") || fs.getDiskUnit().equals("nvme"))
                {
                    // Now we have to compare individual drives/partitions in an OS-dependent fashion
                    if (getDiskUnit().equals("nvme") && !fs.getDiskUnit().equals("nvme"))
                    {
                        return -1;
                    }
                    else if (fs.getDiskUnit().equals("nvme") && !getDiskUnit().equals("nvme"))
                    {
                        return 1;
                    }
                    else
                    {
                        ret = comparePartitions(getPartition(), fs.getPartition());
                    }
                }        
                if (isCDRom())
                {
                    if (!fs.isCDRom())
                    {
                        return 1;
                    }
                    else
                    {
                        return -1;
                    }
                }
                if ((getPartition().length() == 0) && (fs.getPartition().length() > 0))
                {
                    ret = 1;
                }
                else if ((getPartition().length() > 0) && (fs.getPartition().length() == 0))
                {
                    ret = -1;
                }
                else
                {
                    ret = comparePartitions(getPartition(), fs.getPartition());
                }
            }
        }
        else
        {
            // Fall back to sorting by device name
            ret = m_device.compareTo(fs.m_device);
        }
        return ret;
    }
    
    private static Integer compareDriveUnits(String unit1, String unit2)
    {
        Integer ret = 0;
    
        // Mac uses BSD style drive/partition names (i.e. disk0s1, disk1s5, etc)
        if (DiskUsageTest.myOS.getOS() == OS.OSTypes.MAC)
        {
            try
            {
                Integer a = Integer.valueOf(unit1);
                Integer b = Integer.valueOf(unit2);
                ret = a.compareTo(b);
            }
            catch (NumberFormatException e)
            {
                //
            }
        }
        else
        {
            // Linux uses alphabetic (i.e. sda3, sdb7, etc)
            ret = unit1.compareTo(unit2);
        }
        return ret;
    }
    
    
    private Integer comparePartitions(String part1, String part2)
    {
        Integer ret = 0;
        // Mac uses BSD style drive/partition names (i.e. disk0s1, disk1s5, etc)
        if (m_myOS.getOS() == OS.OSTypes.MAC)
        {
            try
            {
                Integer partNumber = Integer.valueOf(part1.substring(1));
                Integer comparePartNumber = Integer.valueOf(part2.substring(1));
                if (m_debug) {
                    System.out.printf("partition Number = %d, comapred to %d\n",
                                       partNumber,
                                       comparePartNumber);
                }
                ret = partNumber.compareTo(comparePartNumber);
            }
            catch (NumberFormatException ne)
            {
                ret = part1.compareTo(part2);
            }
        }
        else
        {
            // Linux uses alphabetic (i.e. sda3, sdb7, etc)
            ret = part1.compareTo(part2);
        }
        return ret;
    }

    public boolean isNVMe() {
        return m_nvme;
    }
    public boolean isNetworkedDevice()

    {
        return (isNFSDevice() || isSMBDevice() || isAutoFSDevice() || isWebDavDevice());
    }

    public boolean isSpecialDevice()
    {
	if (isBadFilesystem()) { return false; }
        String[] special =
        {
            "devfs", "tmpfs", "mtmfs", "cgroup", "configfs", "mqueue", "hugetlbfs", "tmpfs", "debugfs", "bfs",
            "fusectl", "fuse.vmare-vmblock", "binfmt_misc", "tmpfs", "rpc_pipfs", "nfsd", "bpf",
            "devtmpfs", "devpts", "efivarfs", "rpc_pipefs", "sysfs", "fuse.vmware", "securityfs", "proc", 
            "pstore", "autofs", "selinuxfs",  "none", "squashfs", "tracefs","fuse","nullfs"
        };
        for (String s : special)
        {
            if (m_type.trim().startsWith(s))
            {
                if (m_debug) {
                    System.out.printf("Special: Disk Unit '%s', Disk Device '%s', name = '%s', type = '%s', ",
                                      m_diskUnit, m_device, m_name, m_type);
                }
                return true;
            }
        }
        return false;
    }

    public boolean isNFSDevice()
    {
        return (m_type.toLowerCase().startsWith("nfs") && !m_type.toLowerCase().equals("nfsd"));
    }

    public boolean isWebDavDevice()
    {
        return (m_type.equalsIgnoreCase("webdav"));
    }

    public boolean isAutoFSDevice()
    {
        return (m_type.equalsIgnoreCase("autofs"));
    }

    public boolean isSMBDevice()
    {
        return (m_type.equalsIgnoreCase("smbfs"));
    }

    public String getDevice()
    {
        String bd = m_device;
        if (isNetworkedDevice())
        {

            if (isNFSDevice())
            {
                String[] nfs = m_device.split(":");
                if (nfs.length == 2)
                {
                    try
                    {
                        String host = nfs[0];
                        int ind = host.indexOf(".");
                        if (ind != -1)
                        {
                            String h = host.substring(0, ind);
                            if (!h.matches("^\\d*"))
                            {
                                host = h;
                            }
                        }
                        File mt = new File(nfs[1]);
                        String base = mt.getName();
                        String[] comps = mt.getCanonicalPath().split(File.separator);
                        String first = "";
                        if (comps.length > 0)
                        {
                            first = comps[0];
                        }
                        bd = String.format("%s:/%s...%-17.17s", host, first, base);
                    }
                    catch (IOException ex)
                    {
                        System.err.println("Error Parsing NFS Device: " + m_device + ": " + ex);
                    }
                }
            }
            else if (isSMBDevice())
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
                    String d;
                    try 
                    {
                        d = URLDecoder.decode(dev, java.nio.charset.Charset.defaultCharset().toString());
                    }
                    catch (UnsupportedEncodingException ex) 
                    {
                        d = dev;
                    }
                    bd = String.format("//%s/%-17.17s", conn, d);
                }
            }
            else if (isWebDavDevice())
            {
                try
                {
                    URI uri = new URI(m_device);
                    String scheme = uri.getScheme();
                    String host = uri.getHost();
                    int dot = host.indexOf("\\.");
                    if (dot != -1)
                    {
                        host = host.substring(1, dot);
                    }
                   // int port = uri.getPort();
                   // String path = uri.getPath();
                    int len = 17;
                    if (!m_termType.isShortHeader())
                    {
                        len = 32;
                    }
                    String fmt = "%" + len + "." + len + "s";
                    bd = String.format("%s://" + fmt, scheme, host);
                }
                catch (URISyntaxException ex)
                {
                    System.err.println("Error Parsing WebDav Device: " + m_device + ": " + ex);
                }
            }
        }
        return bd;
    }

    public void setDevice(String device)
    {
        this.m_device = device;
    }

    public boolean isRaidDisk()
    {
        return m_raidDisk;
    }
    
    public boolean isLogicalDisk()
    {
        return m_logicalDisk;
    }
    

    public String getDiskUnit()
    {
        if (m_raidDisk) return "RAID";
        if (m_logicalDisk) return "*LD*";
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
        if (m_logicalVolume) return "*LV*";
        if (m_wholeDisk) return "Disk";
        if (m_cdRom) return "*CD*";
        return m_partition;
    }

    public void setPartition(String partition)
    {
        this.m_partition = partition;
    }

    public String getTotalSpace()
    {
        String ret = "---";
        try
        {
            ret = normalizeValue(m_totalSpace, m_kb);
        }
        catch (Exception ex)
        {
            System.err.println("Cannot determine total space: " + ex);
        }
        return ret;
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
        String ret = "? ? ? ?";
        try
        {
            ret = normalizeValue(m_unallocatedSpace, m_kb);
        }
        catch (Exception ex)
        {
            System.err.println("Cannot determine unallocated space: " + ex);
        }
        return ret;
    }

    public void setUnallocatedSpace(long unallocatedSpace)
    {
        this.m_unallocatedSpace = unallocatedSpace;
    }

    public String getUsableSpace()
    {
        String ret = "? ? ? ?";
        if (isCDRom())
        {
            return "<ReadOnly>";
        }
        try
        {
            if ((m_usableSpace == 0) && (m_totalSpace == 0) && (m_unallocatedSpace == 0))
            {
                return String.format("%14.14s", "       ");
            }
            ret = normalizeValue(m_usableSpace, m_kb);
        }
        catch (Exception ex)
        {
            System.err.println("Cannot determine usable space: " + ex);
        }
        return ret;
    }

    public String getUsedSpace()
    {
        String ret = "? ? ? ?";
        if (m_totalSpace == -1)
        {
            return "Unknown";
        }
        double t = (double) m_totalSpace;
        double f = (double) m_unallocatedSpace;
        double used = t - f;
        if ((used == 0) && (t == 0) && (f == 0))
        {
            return String.format("%14.14s", "       ");
        }
        try
        {
            ret = normalizeValue(used, m_kb);
        }
        catch (Exception ex)
        {
            System.err.println("Cannot determine used space: " + ex);
        }
        return ret;
    }

    public void setUsableSpace(long usableSpace)
    {
        this.m_usableSpace = usableSpace;
    }

    String getErrorMessage()
    {
        return (m_errorMessage == null ? null : m_errorMessage.replaceAll("\\n", "->"));
    }

    protected static String getPercentage(long totalSpace, long unallocatedSpace)
    {
        if (totalSpace == -1) return "? ?";
        if (totalSpace == 0)
        {
            return String.format("%3.3s", "---");
        }
        double diff = (totalSpace - unallocatedSpace);
        double pct = diff / (double) totalSpace * (double) 100.0;
        return String.format("%5.2f%%", pct);
        
    }
    String getUsedPercentage()
    {
        if ((m_usableSpace == 0) && (m_totalSpace == 0) && (m_unallocatedSpace == 0))
        {
            return String.format("%3.3s", "   ");                
        }
        return getPercentage(m_totalSpace, m_unallocatedSpace);
    }
    
    private void determineUnitAndPartition(String dev, String dsk, String fakeDevice, File deviceFile)
    {
        switch (m_myOS.getOS())
            {
                case MAC:
                    if (!m_type.equalsIgnoreCase("nfs") && !m_type.equalsIgnoreCase("smbfs"))
                    {
                        if (dev.startsWith("disk"))
                        {
                            dsk = dev.replaceFirst("disk", "").trim();
                        }
                        int ind = dsk.indexOf("s");
                        if (ind == -1) {
                            ind = 1;
                        }
                        m_diskUnit = dsk.substring(0, ind);
                        m_partition = dev.replaceFirst("disk" + m_diskUnit, "").trim();
                        if (m_partition.length() == 0)
                        {
                            m_logicalVolume = true;
                        }
                    }
                    break;
                case LINUX:
              //      System.out.printf("dev = '%s', m_device = '%s'\n", dev, m_device);
                    if (dev.startsWith("sd") || 
                        dev.startsWith("md") || 
                        dev.startsWith("vd") ||
                        dev.startsWith("sr") ||
                        dev.startsWith("nvme") ||
                        m_device.contains("mapper") || 
                        fakeDevice.contains("mapper"))
                    {

                        if (dev.startsWith("md"))
                        {
                            int p = dev.indexOf("p");
                            if (p != -1)
                            {
                                m_raidDisk = true;
                                m_partition = dev.substring(p);
                            }
                            else
                            {
                                m_diskUnit = dev.substring(2, 2);
                                m_partition = dev.substring(3);
                            }
                        }
                        else if (m_device.contains("mapper") || fakeDevice.contains("mapper"))
                        {
                            m_logicalDisk = true;
                            m_logicalVolume = true;
                            try
                            {
                                m_device = deviceFile.getCanonicalPath();
                            }
                            catch (IOException ioe)
                            {
                                m_device = deviceFile.getAbsolutePath();
                            }
                        }
                        else if (dev.startsWith("nvme"))
                            ///dev/nvme0n1p5
                        {
                            m_diskUnit = "NVMe" + dev.substring(4,5);
                            m_partition = dev.substring(7);
                            m_nvme = true;
                        }
                        else
                        {
                            m_diskUnit = dev.substring(2, 3);
                            m_partition = dev.substring(3);
                            if (m_partition.isEmpty())
                            {
                                m_wholeDisk = true;
                            }
                            if (m_type.equalsIgnoreCase("iso9660"))
                            {
                                m_wholeDisk = false;
                                m_cdRom = true;
                            }
                        }
                    }
                    else if (dev.startsWith("xvd"))
                    {
                        m_diskUnit = dev.substring(3, 4);
                        m_partition = dev.substring(4);
                    }
                    break;
                case WINDOWS:
                    break;
                    case UNKNOWN:
                    break;
            }
    }
}
