package mc.alk.arena.controllers.messaging;

import mc.alk.arena.objects.messaging.Message;
import mc.alk.arena.serializers.MessageSerializer;
import mc.alk.arena.util.Log;

public class MessageHandler extends MessageSerializer {

	public MessageHandler() {
		super(null);
	}

	public static String getSystemMessage(String string, Object... varArgs) {
		final Message msg = getDefaultMessage("system."+string);
		if (msg != null){
			final String message = msg.getMessage();
			try{
				return message != null ? String.format(message,varArgs) : null;
			} catch (Exception e){
				final String err = "&c[BA Message Error] system.+"+string;
				Log.err(err);
				for (Object o: varArgs){
					Log.err("Message Option: " + o);}
				e.printStackTrace();
				return err;
			}
		}
		return null;
	}
}
