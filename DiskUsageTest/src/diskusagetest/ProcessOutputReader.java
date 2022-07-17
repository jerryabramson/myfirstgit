package diskusagetest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


public class ProcessOutputReader
    implements Runnable
{
  BufferedReader m_in;
  Exception m_e;
  StringBuilder m_out;

  public ProcessOutputReader(InputStream in, 
                             StringBuilder out)
  {
      m_in = new BufferedReader(new InputStreamReader(in));
      m_out = out;
  }
  
  public void run()
  {
      try
      {
          String line;
          while((line = m_in.readLine()) != null)
          {
              //m_out += line + "\n";
             // System.out.printf("\tDEBUG '%s'\n", line);
              m_out.append(line).append("\n");
          }
      }
      catch(IOException e)
      {
          m_e = e;
      }
      finally
      {
        if (m_in != null)
        {
          // Close the input stream..
          try
          {
            m_in.close();
          }
          catch (Exception e) { } // we are not interested in this exception
        }
        m_out.append("\n");
      }
  }
  
  public String getOutput()
  {
      return m_out.toString();
  }

  
  public Exception getException()
  {
      return m_e;
  }
}
