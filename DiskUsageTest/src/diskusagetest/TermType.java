/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package diskusagetest;


/**
 *
 * @author jerry
 */
public class TermType
{
    // Various Width requirements for different terminal types
    public static final long DEFAULT_MSDOS_CMD_PROMPT_WIDTH = 200;
    public static final long DUMB_COLUMN_WIDTH              = 80;
    public  final long WIDE_COLUMN_WIDTH                    = 135;
    public  final long WIDE_DOS_COLUMN_WIDTH                = 94;    
    
    public  String ANSI_LINE                   = "\u2500";
    public  String BOTTOM_LEFT_CORNER          = "\u2514";
    public  String BOTTOM_RIGHT_CORNER         = "\u2518";
    public  String LEFT_COLUMN_LINE            = "\u251c";
    public  String RIGHT_COLUMN_LINE           = "\u2524";
    public  String TOP_LEFT_CORNER             = "\u250c";
    public  String TOP_RIGHT_CORNER            = "\u2510";
    public  String VERTICAL_BAR                = "\u2502";

    
    private long m_actualColumns = DUMB_COLUMN_WIDTH;
    private final long m_minColumnWidth;
    
    
    private final OS m_myOS;
    private TERM m_term;
    private boolean m_utf;
    
    
    protected static enum TERM {ANSI, DUMB}

    TermType(OS os)
    {
        m_term = TERM.ANSI;
        m_utf = true;
        m_myOS = os;
        
        String termEnv = System.getenv("TERM");
        if (termEnv == null) termEnv = System.getProperty("TERM");
        String lang = System.getenv("LANG");
        if (lang == null) lang = System.getProperty("LANG");     
        if ((termEnv == null) || termEnv.isEmpty() || termEnv.equalsIgnoreCase("dumb")) {
            m_term = TERM.DUMB;
        }
        if ((lang == null) || !lang.toLowerCase().contains("utf")) {
            m_utf = false;
        }
        
        if (!m_utf) {
            VERTICAL_BAR = "|";
            BOTTOM_LEFT_CORNER = "+";
            BOTTOM_RIGHT_CORNER = "+";
            TOP_LEFT_CORNER = "+";
            TOP_RIGHT_CORNER = "+";
            LEFT_COLUMN_LINE = "+";
            RIGHT_COLUMN_LINE = "+";
            ANSI_LINE = "-";
        }
        
        m_actualColumns = determineActualColumns(os, m_term);
        m_minColumnWidth = ((os.getOS() == OS.OSTypes.WINDOWS) ? WIDE_DOS_COLUMN_WIDTH : WIDE_COLUMN_WIDTH);
    }
    
    public  long determineLineLength(long passedLength)
    {
        long len = DUMB_COLUMN_WIDTH - 2;
        if (m_actualColumns < m_minColumnWidth) {
            if (m_myOS.getOS() == OS.OSTypes.WINDOWS) {
                if (m_term != TERM.ANSI) {
                    len = 58;
                } else {
                    len = 64;
                }
            }
        } else {
            len = m_minColumnWidth - 2;
        }
        return len;
    }
    
    public TERM getTerm()                                { return m_term;                               }
    public long getActualColumns()                       { return m_actualColumns;                      }
    public long getMinColumnWidth()                      { return m_minColumnWidth;                     }
    public boolean isAnsiTerm()                          { return (m_term == TERM.ANSI);                }
    public  boolean isShortHeader(int minColumnWidth)    { return (m_actualColumns < minColumnWidth);   }
    public boolean isShortHeader()                       { return (m_actualColumns < m_minColumnWidth); }
    public boolean isUTF()                               { return m_utf;                                }
    
    private  long determineActualColumns(OS os, TermType.TERM term) {
        String colEnv = System.getenv("COLUMNS");
        if (colEnv == null) colEnv = System.getProperty("COLUMNS");
        long cols = -1;
        if (colEnv != null) {
            try {
                cols = Long.valueOf(colEnv);
            } catch (NumberFormatException e) {
                //
            }
        }
        if (cols == -1) {
            cols = DUMB_COLUMN_WIDTH;
            if ((os.getOS() == OS.OSTypes.WINDOWS) && (term != TERM.ANSI)) {
                cols = DEFAULT_MSDOS_CMD_PROMPT_WIDTH;
            }
        }
        return cols;
    }    
}
