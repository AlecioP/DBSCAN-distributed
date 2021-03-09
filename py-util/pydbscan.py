import sys
import numpy as np

from sklearn.cluster import DBSCAN
from sklearn import metrics
from sklearn.datasets import make_blobs
from sklearn.preprocessing import StandardScaler

usage = "[USAGE] python pydbscan.py dataset-file epsilon-val mincount-val"

for arg in sys.argv :
  if ( arg == "-h" or arg == "--help" ): 
    print usage
    exit()

file = sys.argv[1]
epsilon = float(sys.argv[2])
minc = float(sys.argv[3])


X = np.loadtxt(file)

# #############################################################################
# Compute DBSCAN
db = DBSCAN(eps=epsilon, min_samples=minc).fit(X)
core_samples_mask = np.zeros_like(db.labels_, dtype=bool)
core_samples_mask[db.core_sample_indices_] = True
labels = db.labels_

# Number of clusters in labels, ignoring noise if present.
n_clusters_ = len(set(labels)) - (1 if -1 in labels else 0)
n_noise_ = list(labels).count(-1)


# #############################################################################
# Plot result
import matplotlib.pyplot as plt

# Black removed and is used for noise instead.
unique_labels = set(labels)
colors = [plt.cm.Spectral(each)
          for each in np.linspace(0, 1, len(unique_labels))]
for k, col in zip(unique_labels, colors):
    if k == -1:
        # Black used for noise.
        col = [0, 0, 0, 1]

    class_member_mask = (labels == k)

    xy = X[class_member_mask & core_samples_mask]
    plt.plot(xy[:, 0], xy[:, 1], 'o', markerfacecolor=tuple(col),
             markeredgecolor='k', markeredgewidth=0.1, markersize=3)

    xy = X[class_member_mask & ~core_samples_mask]
    plt.plot(xy[:, 0], xy[:, 1], 'o', markerfacecolor=tuple(col),
             markeredgecolor='k', markeredgewidth=0.1, markersize=3)

plt.title('Estimated number of clusters: %d' % n_clusters_)
plt.show()