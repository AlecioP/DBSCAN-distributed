import sys
import numpy as np
import matplotlib.pyplot as plt

usage = "[USAGE] python plotdata.py FILENAME"

for arg in sys.argv :
  if ( arg == "-h" or arg == "--help" ): 
    print usage
    exit()

file = sys.argv[1]

X = np.loadtxt(file)

x = X[:,0]
y = X[:,1]

plt.scatter(x,y)

plt.show()

