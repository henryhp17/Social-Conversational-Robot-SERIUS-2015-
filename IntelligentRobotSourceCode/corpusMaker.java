import java.io.*;

public class corpusMaker{
	
	public static void main(String args[])
	{

		PrintWriter writer = null;
		BufferedReader in = null;
		try{
			writer = new PrintWriter("robotV2.txt", "UTF-8");
		}catch(Exception e)
		{
			System.out.println("error: " + e);
		}

		String name = null;

		try{
			in = new BufferedReader(new FileReader("/Users/brianrhindress/serius/Project Essentials/ProjectCodes/Android/IntelligentRobot/names.txt"));
			while(in.ready())
			{
				name = in.readLine();
				writer.println("hi my name is " + name);
			}
			in.close();
		}catch(Exception e)
		{
			System.out.println("error: " + e);
		}

		try{
			in = new BufferedReader(new FileReader("/Users/brianrhindress/serius/Project Essentials/ProjectCodes/Android/IntelligentRobot/names.txt"));
			while(in.ready())
			{
				name = in.readLine();
				writer.println("i am " + name);
			}
			in.close();
		}catch(Exception e)
		{
			System.out.println("error: " + e);
		}		

		try{
			in = new BufferedReader(new FileReader("/Users/brianrhindress/serius/Project Essentials/ProjectCodes/Android/IntelligentRobot/names.txt"));
			while(in.ready())
			{
				name = in.readLine();
				writer.println("i'm " + name);
			}
		}catch(Exception e)
		{
			System.out.println("error: " + e);
		}		

		writer.close();

	}
}