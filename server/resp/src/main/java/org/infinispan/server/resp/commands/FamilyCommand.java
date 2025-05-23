package org.infinispan.server.resp.commands;

import java.util.List;
import java.util.concurrent.CompletionStage;

import org.infinispan.server.resp.Resp3Handler;
import org.infinispan.server.resp.RespCommand;
import org.infinispan.server.resp.RespRequestHandler;
import org.infinispan.server.resp.commands.scripting.eval.SCRIPT;

import io.netty.channel.ChannelHandlerContext;

/**
 * An umbrella command.
 * <p>
 * This command represents a family of commands. Usually, the ones with multiple keywords, for example,
 * {@link org.infinispan.server.resp.commands.cluster.CLUSTER} or {@link SCRIPT}
 *
 * @since 15.0
 */
public abstract class FamilyCommand extends RespCommand implements Resp3Command {

   public FamilyCommand(int arity, int firstKeyPos, int lastKeyPos, int steps, long aclMask) {
      super(arity, firstKeyPos, lastKeyPos, steps, aclMask);
   }

   @Override
   public CompletionStage<RespRequestHandler> perform(Resp3Handler handler, ChannelHandlerContext ctx, List<byte[]> arguments) {
      byte[] subCommand = arguments.get(0);
      for (RespCommand cmd : getFamilyCommands()) {
         if (cmd.match(subCommand)) {
            if (cmd instanceof Resp3Command) {
               return ((Resp3Command) cmd).perform(handler, ctx, arguments);
            }

            if (cmd instanceof AuthResp3Command) {
               return ((AuthResp3Command) cmd).perform(handler, ctx, arguments);
            }

            break;
         }
      }
      handler.writer().unknownCommand();
      return handler.myStage();
   }

   public abstract RespCommand[] getFamilyCommands();
}
