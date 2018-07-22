import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;

public class CmdTask {

  private CmdWork work;
  private FutureTask<Integer> task;
  private String decoder;

  public CmdTask(
      String decoder,
      String cmd,
      List<String> args,
      Map<String, String> env,
      ExecutorService executor) {
    this.work = new CmdWork(cmd, args, env);
    this.task = new FutureTask<Integer>(work);
    this.decoder = decoder;
    executor.execute(this.task);
  }

  public String getDecoder() {
    return this.decoder;
  }

  public String getOutput() {
    return this.work.getOutput();
  }

  public String getError() {
    return this.work.getError();
  }

  public boolean isDone() {
    return this.task.isDone();
  }

  public long getResponseTime() {
    return this.work.getResponseTime();
  }

  public Integer getReturnCode() {
    try {
      return this.task.get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
