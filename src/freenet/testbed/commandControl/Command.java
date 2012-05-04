package freenet.testbed.commandControl;

public class Command {
	private String name, description;
	private int parameterCount;
	private String[] parameters = null;
	private final int COMMAND_NAME = 0;
	
	public Command(String name, int parameters,String description){
		this.name = name.toLowerCase();
		this.parameterCount = parameters;
		this.description = description;
	}
	
	public boolean isCommand(String command) throws Exception{
		this.parameters = command.split(" ");
		return this.getCommandName().equals(this.name);
	}
	
	public boolean action() throws Exception{
		return false;
	}
	
	public String getCommandName() throws Exception{
		if(this.parameters.length <= COMMAND_NAME)
			throw new Exception("Error interpreting command.");
		return this.parameters[COMMAND_NAME].toLowerCase();
	}
	
	protected String getParameter(int index) throws Exception{
		if(this.parameters.length <= this.parameterCount)
			throw new Exception("The " +index+"th parameter was not found.");
		return this.parameters[index + 1];
	}
	
	protected int getParameterInt(int index) throws Exception{
		if(this.parameters.length <= this.parameterCount)
			throw new Exception("The " +index+"th parameter was not found.");
		return Integer.parseInt(this.parameters[index + 1]);
	}
	
	@Override
	public String toString(){
		StringBuilder b = new StringBuilder();
		b.append(this.name.toUpperCase());
		if(this.parameterCount > 0){
			b.append("[");
			b.append(this.parameterCount);
			b.append("]");
		}
		b.append(" :");
		b.append(this.description);
		return b.toString();
	}
}
