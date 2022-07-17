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
public class AnsiCodes
{

    private static final String ANSI_BOLD = "\033[1m";
    private static final String ANSI_UNDERLINE = "\033[4m";
    private static final String ANSI_RESET = "\033[0m";

    public static String getBold(TermType term)
    {
        return ((term.getTerm() == TermType.TERM.ANSI) ?
                ANSI_BOLD :
                "");
    }

    public static String getUnderline(TermType term)
    {
        return ((term.getTerm() == TermType.TERM.ANSI) ?
                ANSI_UNDERLINE :
                "");
    }

    public static String getReset(TermType term)
    {
        return ((term.getTerm() == TermType.TERM.ANSI) ?
                ANSI_RESET :
                "");
    }

    // standard ANSI color sequences
    public enum ANSI_COLOR
    {
        BLACK, RED, GREEN, YELLOW, BLUE, PURPLE, CYAN, GREY;
        private static final int BASE_COLOR_CODE = 30;
        private static final int BASE_HIGHLIGHT_CODE = 40;

        public static ANSI_COLOR getMin()
        {
            return ANSI_COLOR.BLACK;
        }

        public static ANSI_COLOR getMax()
        {
            return ANSI_COLOR.GREY;
        }

        public int getValue()
        {
            return this.ordinal();
        }

        public String getCode(TermType termType)
        {
            if (termType.getTerm() == TermType.TERM.ANSI)
            {
                return String.format("\033[%dm", getValue() + BASE_COLOR_CODE);
            }
            else
            {
                return "";
            }
        }

        public String getBoldCode(TermType termType)
        {
            if (termType.getTerm() == TermType.TERM.ANSI)
            {
                return String.format("\033[%d;1m", getValue() + BASE_COLOR_CODE);
            }
            else
            {
                return "";
            }
        }

        public String getHighlightCode(TermType termType)
        {
            if (termType.getTerm() == TermType.TERM.ANSI)
            {
                return String.format("\033[%dm", getValue() + BASE_HIGHLIGHT_CODE);
            }
            else
            {
                return "";
            }
        }

        public String getReverseHighlightCode(TermType termType)
        {
            if (termType.getTerm() == TermType.TERM.ANSI)
            {
                return String.format("\033[%d;1m", getValue() + BASE_HIGHLIGHT_CODE);
            }
            else
            {
                return "";
            }
        }

        public static ANSI_COLOR next(ANSI_COLOR currentColor)
        {
            if (currentColor == getMax())
            {
                return getMin();
            }
            else
            {
                ANSI_COLOR n = ANSI_COLOR.values()[currentColor.ordinal() + 1];
                return n;
            }
        }

    }
}


