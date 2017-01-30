import csv
import numpy as np
import pandas as pd  
import matplotlib
import matplotlib.pyplot as plt
import json
%matplotlib inline  

plotsetting_default = {'color': 'r', 'linewidth': 2}
plotsetting = {'color': 'r', 'linewidth': 2}

def plot_cumavg(x, y, xlabel='query#', ylabel='recall', title=None):
    y_cum = np.cumsum(y).tolist()
    #print accepts_np[:,1]
    y_cumavg = [cum / float(count+1) for count,cum in enumerate(y_cum)]
    #N = 500;
    #y_cumavg = np.convolve(np.array(y), np.ones((N,))/N, mode='same').tolist()

    #print accept_rate 
    
    #plt.scatter(means_baseline[0:], means[0:], s=colors, alpha=0.8, c='r')
    plt.plot(x, y_cumavg, alpha=0.5, **plotsetting)
    
    plt.xlabel(xlabel, fontsize=12)
    plt.ylabel(ylabel, fontsize=12)
    # plt.xlim(0, 0.65)
    plt.ylim(0, max(y_cumavg)*1.02)
    
    xp = np.linspace(0, 0.65, 300)
    
    #plt.gca().set_aspect('equal', adjustable='box')
    plottitle = title if title is not None else '%s_vs_%s.pdf' % (xlabel, ylabel)
    plt.savefig(plottitle ,  bbox_inches="tight")
    
def print_avg(x, name = 'unnamed'):
    print 'Average of %s is %f' % (name, reduce(lambda a,b: a+b, x) / float(len(x)));
    
with open('../state/lastExec', 'rb') as lastExec:
    lastExecInd = lastExec.readline().strip()
print lastExecInd
     
rows = []; 
execInd = lastExecInd;
with open('../state/execs/%s.exec/plotInfo.json' % execInd, 'rb') as jsonfile:
    json_lines = jsonfile.readlines()
    
rows = [json.loads(l) for l in json_lines]
# accepts = [[r['queryCount'], 1 if r['stats.rank']>=0 else 0] for r in rows if r['stats.type'] == 'accept'];

def percent_induced_in_accepted(status = 'Core'):
    filtered_rows = [r for r in rows if r['stats.type'] == 'accept']
    query_counts = [r['queryCount'] for r in filtered_rows]
    is_induced = [1 if r['stats.status'] == status else 0 for r in filtered_rows]
    print_avg(is_induced, 'percent_induced_accepted')
    plotsetting['label'] = status.lower();
    plot_cumavg(query_counts, is_induced, xlabel='query #', ylabel='induced-accepted');
# plt.figure()
# plotsetting['color'] = 'r'; percent_induced_in_accepted(status = 'Nothing');
# plotsetting['color'] = 'b'; percent_induced_in_accepted(status = 'Induced');
# plotsetting['color'] = 'k'; percent_induced_in_accepted(status = 'Core');
# plt.legend(frameon=False)
# plt.savefig('accepted_stats.pdf' , bbox_inches="tight")


def precent_status(status = 'Core'):
    filtered_rows = [r for r in rows if r['stats.type'] == 'q']
    query_counts = [r['queryCount'] for r in filtered_rows]
    is_status = [1 if r['stats.status'] == status else 0 for r in filtered_rows]
    print_avg(is_status, 'percent of status ' + status)
    plotsetting['label'] = status.lower();
    plot_cumavg(query_counts, is_status, xlabel='query #', ylabel='%');
plt.figure()
plotsetting['color'] = 'r'; precent_status(status = 'Nothing');
plotsetting['color'] = 'b'; precent_status(status = 'Induced');
plotsetting['color'] = 'k'; precent_status(status = 'Core');
plt.legend(frameon=False)
plt.savefig('parse_status.pdf' , bbox_inches="tight")

def simple_acc():
    filtered_rows = [r for r in rows if r['stats.type'] == 'accept' or r['stats.type'] == 'q']
    query_counts = [r['queryCount'] for r in filtered_rows]
    accepts = [1 if r['stats.type'] != 'q' and r['stats.rank']==0 else 0 for r in filtered_rows]
    plot_cumavg(query_counts, accepts, xlabel='query#', ylabel='accuracy');
    print_avg(accepts, 'accuracy')
plt.figure()
simple_acc()

def simple_recall():
    filtered_rows = [r for r in rows if r['stats.type'] == 'accept']
    query_counts = [r['queryCount'] for r in filtered_rows]
    recall = [1 if r['stats.rank']>=0 else 0 for r in filtered_rows]
    plot_cumavg(query_counts, recall, xlabel='query#', ylabel='recall');
    print_avg(recall, 'recall')
plotsetting['color'] = 'r'
simple_recall()

def average_stat(stat = 'stats.size', type = 'q'):
    filtered_rows = [r for r in rows if r['stats.type'] == type]
    query_counts = [r['queryCount'] for r in filtered_rows]
    stats = [r[stat] for r in filtered_rows]
    plot_cumavg(query_counts, stats, xlabel='query#', ylabel=stat);
    print_avg(stats, stat)
plt.figure()
plotsetting['color'] = 'b'
average_stat(stat = 'stats.size')


def average_stat(stat = 'stats.rank'):
    filtered_rows = [r for r in rows if r['stats.type'] == 'accept' and r['stats.rank'] >= 0]
    query_counts = [r['queryCount'] for r in filtered_rows]
    stats = [r[stat] for r in filtered_rows]
    plot_cumavg(query_counts, stats, xlabel='query#', ylabel=stat);
    print_avg(stats, stat)
plt.figure()
average_stat(stat = 'stats.rank')
