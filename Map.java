import jade.util.leap.Serializable;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class Map implements Serializable, Cloneable {

    private int numRows;
    private int numCols;
    private int[][] mapMatrix; // 0 represents empty position, 1 item, and 2 trap
    private int numTraps;
    private int numItems;
                    
    public Map(int numRows, int numCols, int numItems, int numTraps) throws Exception
    {
        this.numRows = numRows;
        this.numCols = numCols;
        this.numTraps = numTraps;
        this.numItems = numItems;

        // Initialize map to zero's
        mapMatrix = new int[numRows][numCols];
        for (int k = 0; k < numRows; k++)
            for (int k1 = 0; k1 < numCols; k1++)
                mapMatrix[k][k1] = 0;

        // create instance of Random class
        Random rand = new Random();

        // Generate items
        int generatedItems = 0;
        for (int k = 0; k < numItems; k++)
        {
            int tempRow = rand.nextInt(numRows); // 0...numRows-1
            int tempCol = rand.nextInt(numCols); // 0...numCols-1
            if (mapMatrix[tempRow][tempCol] == 0)
            {
                mapMatrix[tempRow][tempCol] = 1;
                generatedItems++;
                continue;
            }
            else {
                //System.out.println("Attempted position for item not empty, finding alternative...");
                // Search for next available position
                int newTempRow = tempRow;
                int newTempCol = tempCol;
                outerloop:
                for (int k1 = 0; k1 < numRows; k1++) 
                {
                    for (int k2 = 0; k2 < numCols; k2++)
                    {
                        newTempRow = (tempRow + k1) % numRows;
                        newTempCol = (tempCol + k2) % numCols;
                        if (mapMatrix[newTempRow][newTempCol] == 0)
                        {
                            mapMatrix[newTempRow][newTempCol] = 1;
                            generatedItems++;
                            break outerloop;
                        }
                    }
                }
            }
        }
        if (generatedItems != numItems)
            throw new Exception("Unable to find empty position to accomodate for all items while generating map");

        // Generate traps
        generatedItems = 0;
        for (int k = 0; k < numTraps; k++)
        {
            int tempRow = rand.nextInt(numRows); // 0...numRows-1
            int tempCol = rand.nextInt(numCols); // 0...numCols-1
            if (mapMatrix[tempRow][tempCol] == 0)
            {
                mapMatrix[tempRow][tempCol] = 2;
                generatedItems++;
                continue;
            }
            else {
                //System.out.println("Attempted position for trap not empty, finding alternative...");
                // Search for next available position
                int newTempRow = tempRow;
                int newTempCol = tempCol;
                outerloop:
                for (int k1 = 0; k1 < numRows; k1++) 
                {
                    for (int k2 = 0; k2 < numCols; k2++)
                    {
                        newTempRow = (tempRow + k1) % numRows;
                        newTempCol = (tempCol + k2) % numCols;
                        if (mapMatrix[newTempRow][newTempCol] == 0)
                        {
                            mapMatrix[newTempRow][newTempCol] = 2;
                            generatedItems++;
                            break outerloop;
                        }
                    }
                }
            }
        }
        if (generatedItems != numTraps)
            throw new Exception("Unable to find empty position to accomodate for all traps while generating map");

    }

    /* Shows the map through the command line */
    public void show() {
        for (int k = 0; k < mapMatrix.length; k++) {
            for (int k1 = 0; k1 < mapMatrix[k].length; k1++) {
                System.out.print(mapMatrix[k][k1] + " ");
            }
            System.out.println();
        }
    }

    /* Returns string representing the map */
    @Override
    public String toString()
    {
        String mapStr = "";
        for (int k = 0; k < mapMatrix.length; k++) {
            for (int k1 = 0; k1 < mapMatrix[k].length; k1++) {
                mapStr += mapMatrix[k][k1] + " ";
            }
            mapStr += "\n";
        }
        return mapStr;
    }

    /* Returns string representing the map with certain positions colored highlighted
       Returns positions highlighted in map with Ansi sequence escape colors
       Warning!!: only 6 colors, otherwise colors will be ignored
       Needs to be executed in command line supporting this functionality */
    public String toString(LinkedList<Position> positions)
    {
        if (positions.size() > 6)
        {
            System.out.println("Warning: More than 6 positions to color, ignoring colors...");
            return toString();
        }
            
        // List of (6) AnsiColors
        List<String> ansiColors = Arrays.asList("\033[0;31m", "\033[0;32m", "\033[0;33m", "\033[0;34m", "\033[0;35m", "\033[0;36m");
        
        String mapStr = "";
        for (int k = 0; k < mapMatrix.length; k++) {
            for (int k1 = 0; k1 < mapMatrix[k].length; k1++) {
                int idx = positions.indexOf(new Position(k,k1));
                if (idx != -1)
                {
                    mapStr += ansiColors.get(idx) + mapMatrix[k][k1] + "\033[0m ";
                }
                else
                {
                    mapStr += mapMatrix[k][k1] + " ";
                }
            }
            mapStr += "\n";
        }
        return mapStr;
    }

    public LinkedList<Position> getItemPositions()
    {
        LinkedList<Position> positions = new LinkedList<Position>();
        
        for (int k = 0; k < mapMatrix.length; k++) {
            for (int k1 = 0; k1 < mapMatrix[k].length; k1++) {
                if (mapMatrix[k][k1] == 1)
                {
                    positions.add(new Position(k,k1));
                }
            }
        }

        return positions;
    }

    public LinkedList<Position> getTrapsPositions()
    {
        LinkedList<Position> positions = new LinkedList<Position>();
        
        for (int k = 0; k < mapMatrix.length; k++) {
            for (int k1 = 0; k1 < mapMatrix[k].length; k1++) {
                if (mapMatrix[k][k1] == 2)
                {
                    positions.add(new Position(k,k1));
                }
            }
        }

        return positions;
    }

    public void clearPosition(Position pos)
    {
        mapMatrix[pos.x][pos.y] = 0;
    }

    public void generateNewItem()
    {
        // create instance of Random class
        Random rand = new Random();

        boolean itemGenerated = false;

        int tempRow = rand.nextInt(numRows); // 0...numRows-1
        int tempCol = rand.nextInt(numCols); // 0...numCols-1
        if (mapMatrix[tempRow][tempCol] == 0)
        {
            mapMatrix[tempRow][tempCol] = 1;
            itemGenerated = true;
        }
        else {
            //System.out.println("Attempted position for item not empty, finding alternative...");
            // Search for next available position
            int newTempRow = tempRow;
            int newTempCol = tempCol;
            outerloop:
            for (int k1 = 0; k1 < numRows; k1++) 
            {
                for (int k2 = 0; k2 < numCols; k2++)
                {
                    newTempRow = (tempRow + k1) % numRows;
                    newTempCol = (tempCol + k2) % numCols;
                    if (mapMatrix[newTempRow][newTempCol] == 0)
                    {
                        mapMatrix[newTempRow][newTempCol] = 1;
                        itemGenerated = true;
                        break outerloop;
                    }
                }
            }
        }
           
        try {
            if (!itemGenerated)
                throw new Exception("Unable to find empty position to accomodate for all items while generating map");
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public Position searchRandomEmtpyPosition()
    {
        Position emptyPos = null;
        
        // create instance of Random class
        Random rand = new Random();

        boolean positionFound = false;

        int tempRow = rand.nextInt(numRows); // 0...numRows-1
        int tempCol = rand.nextInt(numCols); // 0...numCols-1
        if (mapMatrix[tempRow][tempCol] == 0)
        {
            emptyPos = new Position(tempRow, tempCol);
            positionFound = true;
        }
        else {
            //System.out.println("Position not empty, finding alternative...");
            // Search for next available position
            int newTempRow = tempRow;
            int newTempCol = tempCol;
            outerloop:
            for (int k1 = 0; k1 < numRows; k1++) 
            {
                for (int k2 = 0; k2 < numCols; k2++)
                {
                    newTempRow = (tempRow + k1) % numRows;
                    newTempCol = (tempCol + k2) % numCols;
                    if (mapMatrix[newTempRow][newTempCol] == 0)
                    {
                        emptyPos = new Position(tempRow, tempCol);
                        positionFound = true;
                        break outerloop;
                    }
                }
            }
        }
           
        try {
            if (!positionFound)
                throw new Exception("Unable to find empty position");
        } catch (Exception e)
        {
            e.printStackTrace();
        }

        return emptyPos;
    }

    public void redistributeTraps()
    {
        // create instance of Random class
        Random rand = new Random();

        LinkedList<Position> trapPositions = getTrapsPositions();
        for(Position trapPos : trapPositions)
            clearPosition(trapPos);

        // Generate traps
        int generatedItems = 0;
        for (int k = 0; k < numTraps; k++)
        {
            int tempRow = rand.nextInt(numRows); // 0...numRows-1
            int tempCol = rand.nextInt(numCols); // 0...numCols-1
            if (mapMatrix[tempRow][tempCol] == 0)
            {
                mapMatrix[tempRow][tempCol] = 2;
                generatedItems++;
                continue;
            }
            else {
                //System.out.println("Attempted position for trap not empty, finding alternative...");
                // Search for next available position
                int newTempRow = tempRow;
                int newTempCol = tempCol;
                outerloop:
                for (int k1 = 0; k1 < numRows; k1++) 
                {
                    for (int k2 = 0; k2 < numCols; k2++)
                    {
                        newTempRow = (tempRow + k1) % numRows;
                        newTempCol = (tempCol + k2) % numCols;
                        if (mapMatrix[newTempRow][newTempCol] == 0)
                        {
                            mapMatrix[newTempRow][newTempCol] = 2;
                            generatedItems++;
                            break outerloop;
                        }
                    }
                }
            }
        }
        try {
            if (generatedItems != numTraps)
            throw new Exception("Unable to find empty position to accomodate for all traps while rescheduling");
        } catch (Exception e)
        {
            e.printStackTrace();
        }
        
    }

    public void redistributeItems()
    {
        // create instance of Random class
        Random rand = new Random();

        LinkedList<Position> itemPositions = getItemPositions();
        for(Position itemPos : itemPositions)
            clearPosition(itemPos);

        // Generate traps
        int generatedItems = 0;
        for (int k = 0; k < numItems; k++)
        {
            int tempRow = rand.nextInt(numRows); // 0...numRows-1
            int tempCol = rand.nextInt(numCols); // 0...numCols-1
            if (mapMatrix[tempRow][tempCol] == 0)
            {
                mapMatrix[tempRow][tempCol] = 1;
                generatedItems++;
                continue;
            }
            else {
                //System.out.println("Attempted position for item not empty, finding alternative...");
                // Search for next available position
                int newTempRow = tempRow;
                int newTempCol = tempCol;
                outerloop:
                for (int k1 = 0; k1 < numRows; k1++) 
                {
                    for (int k2 = 0; k2 < numCols; k2++)
                    {
                        newTempRow = (tempRow + k1) % numRows;
                        newTempCol = (tempCol + k2) % numCols;
                        if (mapMatrix[newTempRow][newTempCol] == 0)
                        {
                            mapMatrix[newTempRow][newTempCol] = 2;
                            generatedItems++;
                            break outerloop;
                        }
                    }
                }
            }
        }
        try {
            if (generatedItems != numItems)
            throw new Exception("Unable to find empty position to accomodate for all items while rescheduling");
        } catch (Exception e)
        {
            e.printStackTrace();
        }
        
    }

    public void redistributeMap()
    {
        redistributeItems();
        redistributeTraps();
    }

    public boolean withinMapLimits(Position pos)
    {
        return (pos.x >= 0 && pos.x < mapMatrix.length) &&
            (pos.y >= 0 && pos.y < mapMatrix.length); 
    }

    public boolean isItemPosition(Position pos)
    {
        LinkedList<Position> itemPositions = getItemPositions();

        return itemPositions.contains(pos);
    }

    public boolean isTrapPosition(Position pos)
    {
        LinkedList<Position> trapsPositions = getTrapsPositions();

        return trapsPositions.contains(pos);
    }

    @Override
	public Object clone() throws CloneNotSupportedException {
	    Map mapCopy = (Map) super.clone();
        
        mapCopy.mapMatrix = new int[numRows][numCols];
        for (int k = 0; k < numRows; k++)
            for (int k1 = 0; k1 < numCols; k1++)
                mapCopy.mapMatrix[k][k1] = mapMatrix[k][k1];

        return mapCopy;
	}

}
