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
public class OS
{
    protected enum OSTypes
    {
        LINUX,
        MAC,
        WINDOWS,
        UNKNOWN
    }
    OSTypes myOS;
    
    
    public OS()
    {
        String os = System.getProperty("os.name");
      //  System.out.printf("os = '%s'\n", os);
        switch (os.toLowerCase().substring(0, 3))
        {
            case "mac":
                myOS = OSTypes.MAC;
                break;
            case "lin":
                myOS = OSTypes.LINUX;
                break;
            case "win":
                myOS = OSTypes.WINDOWS;
                break;
            default:
                myOS = OSTypes.UNKNOWN;
                break;
        }
    }
    
    public OSTypes getOS()
    {
        return myOS;
    }
    
    public static int execCommand(String cmd, StringBuilder output, StringBuilder error)
            throws Exception
    {
        int exitCode = -999;
        
        try
      {

       //   System.out.printf("Executing the command: ==>%s<==\n" , cmd);

        Process proc;
        Runtime runtime = Runtime.getRuntime();
        proc = runtime.exec(cmd);

//        PrintWriter pw = new PrintWriter(new OutputStreamWriter(proc.getOutputStream(), Charset.defaultCharset()));
//        pw.flush();

        ProcessOutputReader procOut = new ProcessOutputReader(proc.getInputStream(), output);
        Thread outThread = new Thread(procOut);
        outThread.start();
        ProcessOutputReader procErr = new ProcessOutputReader(proc.getErrorStream(), error);
        Thread errThread = new Thread(procErr);
        errThread.start();

        proc.waitFor();
        outThread.join();
        errThread.join();
        exitCode = proc.exitValue();
        //System.out.printf("\texit code = %d\n", exitCode);
        //System.out.printf("\t\tstdout is '%s'\n", stdout);
        //System.out.printf("\t\tstderr is '%s'\n", stderr);

        Exception threadExcep;
        if((threadExcep = procOut.getException()) != null)
        {
          throw threadExcep;
        }
        if((threadExcep = procErr.getException()) != null)
        {
          throw threadExcep;
        }

      }
      catch (Exception ex)
      {
       System.out.println("Exception occured while executing the command " +
                      cmd + " :\n" +
                      ex.getMessage());
      }
      return exitCode;
    }
}
