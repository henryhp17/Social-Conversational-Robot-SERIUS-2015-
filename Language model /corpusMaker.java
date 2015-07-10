import java.io.*;

public class corpusMaker{
	
	public static void main(String args[])
	{


		String outFileName = "CHOOSENAMEHERE.txt";
		PrintWriter writer = null;
		BufferedReader in = null;
		try{
			writer = new PrintWriter("outFileName", "UTF-8");
		}catch(Exception e)
		{
			System.out.println("error: " + e);
		}

		String word = null;

		try{
			in = new BufferedReader(new FileReader("names2.txt"));
			while(in.ready())
			{
				word = in.readLine();
				writer.println("hi my name is " + word);
			}
			in.close();
		}catch(Exception e)
		{
			System.out.println("error: " + e);
		}

		try{
			in = new BufferedReader(new FileReader("names2.txt"));
			while(in.ready())
			{
				word = in.readLine();
				writer.println("i am " + word);
			}
			in.close();
		}catch(Exception e)
		{
			System.out.println("error: " + e);
		}		

		try{
			in = new BufferedReader(new FileReader("names2.txt"));
			while(in.ready())
			{
				word = in.readLine();
				writer.println("i'm " + word);
			}
		}catch(Exception e)
		{
			System.out.println("error: " + e);
		}	


		try{
			in = new BufferedReader(new FileReader("colors2.txt"));
			while(in.ready())
			{
				word = in.readLine();
				writer.println("my favorite color is " + word);
			}
		}catch(Exception e)
		{
			System.out.println("error: " + e);
		}	

		try{
			in = new BufferedReader(new FileReader("colors2.txt"));
			while(in.ready())
			{
				word = in.readLine();
				writer.println("i like the color " + word);
			}
		}catch(Exception e)
		{
			System.out.println("error: " + e);
		}	

		try{
			in = new BufferedReader(new FileReader("colors2.txt"));
			while(in.ready())
			{
				word = in.readLine();
				writer.println("i really like " + word);
			}
		}catch(Exception e)
		{
			System.out.println("error: " + e);
		}	

				try{
			in = new BufferedReader(new FileReader("colors2.txt"));
			while(in.ready())
			{
				word = in.readLine();
				writer.println("i like " + word);
			}
		}catch(Exception e)
		{
			System.out.println("error: " + e);
		}	


		writer.println("i want to play with a puppy");
		writer.println("i could use a puppy");
		writer.println("i want a puppy");
		writer.println("i feel sad");
		writer.println("i’m sad");
		writer.println("i am sad");
		writer.println("i am kind of sad");
		writer.println("i am feeling sad");
		writer.println("i'm feeling sad");
		writer.println("i'm really sad");
		writer.println("i am really sad");

		writer.println("i need a wing man");
		writer.println("i could use a wing man");
		writer.println("i’m lonely");
		writer.println("i’m alone");
		writer.println("i am alone");
		writer.println("i am feeling alone");
		writer.println("i'm feeling alone");
		writer.println("i am feeling lonely");
		writer.println("i'm feeling lonely");

		writer.println("i am lost");
		writer.println("actually, i’m lost");
		writer.println("navigation mode");
		writer.println("i want to navigate");
		writer.println("could you help me find my way?");

		writer.println("bye");
		writer.println("good bye");
		writer.println("i have to go now, bye bye");
		writer.println("goodbye");
		
		writer.println("forward ");
		writer.println("back");
		writer.println("left");
		writer.println("right");
		writer.println("stop");
		writer.println("picture");
		writer.println("restart");
		writer.close();

	}
}