(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[4],[],[],[],[],[],[],[],[3],[2],[],[],[2],[],[],[],[],[],[],[],[],[],[],[],[2],[],[],[0],[],[3],[],[],[0],[],[],[],[0],[],[],[],[],[0],[],[0],[],[],[],[],[],[],[],[],[],[4],[4],[0],[],[],[3]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:09:58.605)
  (NBestInd 5)
  (utterance "remove red")
  (definition "remove if top red")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.has (number 2 COLOR))
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
      )
    )
  )
  (targetValue
    (string
      [[],[],[],[],[],[4],[],[],[],[],[],[],[],[3],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[0],[],[3],[],[],[0],[],[],[],[0],[],[],[],[],[0],[],[0],[],[],[],[],[],[],[],[],[],[4],[4],[0],[],[],[3]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[4],[],[],[],[],[],[],[],[3],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[0],[],[3],[],[],[0],[],[],[],[0],[],[],[],[],[0],[],[0],[],[],[],[],[],[],[],[],[],[4],[4],[0],[],[],[3]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:10:05.441)
  (NBestInd 14)
  (utterance "add red")
  (definition "add red")
  (targetFormula (call context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 2 COLOR))))
  (targetValue
    (string
      [[2],[2],[2],[2],[2],[4,2],[2],[2],[2],[2],[2],[2],[2],[3,2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[0,2],[2],[3,2],[2],[2],[0,2],[2],[2],[2],[0,2],[2],[2],[2],[2],[0,2],[2],[0,2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[4,2],[4,2],[0,2],[2],[2],[3,2]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[2],[2],[2],[2],[2],[4,2],[2],[2],[2],[2],[2],[2],[2],[3,2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[0,2],[2],[3,2],[2],[2],[0,2],[2],[2],[2],[0,2],[2],[2],[2],[2],[0,2],[2],[0,2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[4,2],[4,2],[0,2],[2],[2],[3,2]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:10:13.091)
  (NBestInd 1)
  (utterance "remove red")
  (definition "remove if top red")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.has (number 2 COLOR)))
        )
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
      )
    )
  )
  (targetValue
    (string
      [[],[],[],[],[],[4],[],[],[],[],[],[],[],[3],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[0],[],[3],[],[],[0],[],[],[],[0],[],[],[],[],[0],[],[0],[],[],[],[],[],[],[],[],[],[4],[4],[0],[],[],[3]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[4],[],[],[],[],[],[],[],[3],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[0],[],[3],[],[],[0],[],[],[],[0],[],[],[],[],[0],[],[0],[],[],[],[],[],[],[],[],[],[4],[4],[0],[],[],[3]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:10:24.210)
  (NBestInd 2)
  (utterance "add red to yellow")
  (definition "add red if top yellow")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.has (number 4 COLOR))
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 2 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[],[],[],[],[],[4,2],[],[],[],[],[],[],[],[3],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[0],[],[3],[],[],[0],[],[],[],[0],[],[],[],[],[0],[],[0],[],[],[],[],[],[],[],[],[],[4,2],[4,2],[0],[],[],[3]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[4,2],[],[],[],[],[],[],[],[3],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[0],[],[3],[],[],[0],[],[],[],[0],[],[],[],[],[0],[],[0],[],[],[],[],[],[],[],[],[],[4,2],[4,2],[0],[],[],[3]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:10:31.249)
  (NBestInd 3)
  (utterance "add yellow to red")
  (definition "add yellow if top red")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.has (number 2 COLOR)))
        )
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 4 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[],[],[],[],[],[4,2,4],[],[],[],[],[],[],[],[3],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[0],[],[3],[],[],[0],[],[],[],[0],[],[],[],[],[0],[],[0],[],[],[],[],[],[],[],[],[],[4,2,4],[4,2,4],[0],[],[],[3]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[4,2,4],[],[],[],[],[],[],[],[3],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[0],[],[3],[],[],[0],[],[],[],[0],[],[],[],[],[0],[],[0],[],[],[],[],[],[],[],[],[],[4,2,4],[4,2,4],[0],[],[],[3]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:10:57.431)
  (NBestInd 3)
  (utterance "add cyan to yellow")
  (definition "add cyan if top yellow")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.has (number 4 COLOR)))
        )
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 0 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[],[],[],[],[],[4,2,4,0],[],[],[],[],[],[],[],[3],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[0],[],[3],[],[],[0],[],[],[],[0],[],[],[],[],[0],[],[0],[],[],[],[],[],[],[],[],[],[4,2,4,0],[4,2,4,0],[0],[],[],[3]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[4,2,4,0],[],[],[],[],[],[],[],[3],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[0],[],[3],[],[],[0],[],[],[],[0],[],[],[],[],[0],[],[0],[],[],[],[],[],[],[],[],[],[4,2,4,0],[4,2,4,0],[0],[],[],[3]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:11:05.528)
  (NBestInd 1)
  (utterance "remove orange")
  (definition "remove if top orange")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.has (number 3 COLOR)))
        )
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
      )
    )
  )
  (targetValue
    (string
      [[],[],[],[],[],[4,2,4,0],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[0],[],[],[],[],[0],[],[],[],[0],[],[],[],[],[0],[],[0],[],[],[],[],[],[],[],[],[],[4,2,4,0],[4,2,4,0],[0],[],[],[]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[4,2,4,0],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[0],[],[],[],[],[0],[],[],[],[0],[],[],[],[],[0],[],[0],[],[],[],[],[],[],[],[],[],[4,2,4,0],[4,2,4,0],[0],[],[],[]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:11:45.227)
  (NBestInd 161)
  (utterance "replace cyan by red")
  (definition "[ remove then add red ] if top cyan")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.has (number 0 COLOR))
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.seq
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 2 COLOR))
        )
      )
    )
  )
  (targetValue
    (string
      [[],[],[],[],[],[4,2,4,2],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[2],[],[],[],[],[2],[],[],[],[2],[],[],[],[],[2],[],[2],[],[],[],[],[],[],[],[],[],[4,2,4,2],[4,2,4,2],[2],[],[],[]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[4,2,4,2],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[2],[],[],[],[],[2],[],[],[],[2],[],[],[],[],[2],[],[2],[],[],[],[],[],[],[],[],[],[4,2,4,2],[4,2,4,2],[2],[],[],[]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:12:16.502)
  (NBestInd 20)
  (utterance "replace red by cyan")
  (definition "[ remove then add cyan ] if top red")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.has (number 2 COLOR))
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.seq
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 0 COLOR))
        )
      )
    )
  )
  (targetValue
    (string
      [[],[],[],[],[],[4,2,4,0],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[0],[],[],[],[],[0],[],[],[],[0],[],[],[],[],[0],[],[0],[],[],[],[],[],[],[],[],[],[4,2,4,0],[4,2,4,0],[0],[],[],[]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[4,2,4,0],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[0],[],[],[],[],[0],[],[],[],[0],[],[],[],[],[0],[],[0],[],[],[],[],[],[],[],[],[],[4,2,4,0],[4,2,4,0],[0],[],[],[]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:12:32.981)
  (NBestInd 2)
  (utterance "add red to cyan")
  (definition "add red if top cyan")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.has (number 0 COLOR)))
        )
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 2 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[],[],[],[],[],[4,2,4,0,2],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[0,2],[],[],[],[],[0,2],[],[],[],[0,2],[],[],[],[],[0,2],[],[0,2],[],[],[],[],[],[],[],[],[],[4,2,4,0,2],[4,2,4,0,2],[0,2],[],[],[]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[],[],[],[],[],[2],[],[],[],[1],[],[],[2],[1],[],[],[],[],[],[],[],[],[],[],[0],[],[],[1],[1],[],[],[],[],[],[],[],[4],[],[3],[3],[],[],[0],[],[],[],[],[],[],[],[],[],[3],[],[],[],[],[1],[]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:12:57.867)
  (NBestInd 5)
  (utterance "replace orange by brown")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.top (number 3 COLOR))
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.seq
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 1 COLOR))
        )
      )
    )
  )
  (targetValue
    (string
      [[],[],[],[],[],[],[],[],[],[],[2],[],[],[],[1],[],[],[2],[1],[],[],[],[],[],[],[],[],[],[],[0],[],[],[1],[1],[],[],[],[],[],[],[],[4],[],[1],[1],[],[],[0],[],[],[],[],[],[],[],[],[],[1],[],[],[],[],[1],[]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[],[],[],[],[],[2],[],[],[],[1],[],[],[2],[1],[],[],[],[],[],[],[],[],[],[],[0],[],[],[1],[1],[],[],[],[],[],[],[],[4],[],[1],[1],[],[],[0],[],[],[],[],[],[],[],[],[],[1],[],[],[],[],[1],[]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:13:07.346)
  (NBestInd 0)
  (utterance "replace cyan by red")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.has (number 0 COLOR))
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.seq
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 2 COLOR))
        )
      )
    )
  )
  (targetValue
    (string
      [[],[],[],[],[],[],[],[],[],[],[2],[],[],[],[1],[],[],[2],[1],[],[],[],[],[],[],[],[],[],[],[2],[],[],[1],[1],[],[],[],[],[],[],[],[4],[],[1],[1],[],[],[2],[],[],[],[],[],[],[],[],[],[1],[],[],[],[],[1],[]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[],[],[],[],[],[2],[],[],[],[1],[],[],[2],[1],[],[],[],[],[],[],[],[],[],[],[2],[],[],[1],[1],[],[],[],[],[],[],[],[4],[],[1],[1],[],[],[2],[],[],[],[],[],[],[],[],[],[1],[],[],[],[],[1],[]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:13:30.924)
  (NBestInd 20)
  (utterance "add yellow and then red")
  (definition "add yellow then add red")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.seq
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 4 COLOR))
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 2 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[2,4,2],[4,2],[4,2],[4,2],[1,4,2],[4,2],[4,2],[2,4,2],[1,4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[2,4,2],[4,2],[4,2],[1,4,2],[1,4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,4,2],[4,2],[1,4,2],[1,4,2],[4,2],[4,2],[2,4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[1,4,2],[4,2],[4,2],[4,2],[4,2],[1,4,2],[4,2]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[2,4,2],[4,2],[4,2],[4,2],[1,4,2],[4,2],[4,2],[2,4,2],[1,4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[2,4,2],[4,2],[4,2],[1,4,2],[1,4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,4,2],[4,2],[1,4,2],[1,4,2],[4,2],[4,2],[2,4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[4,2],[1,4,2],[4,2],[4,2],[4,2],[4,2],[1,4,2],[4,2]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:13:41.116)
  (NBestInd 2)
  (utterance "add brown and then cyan")
  (definition "add brown then add cyan")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.seq
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 0 COLOR))
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 1 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[2,4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[1,4,2,0,1],[4,2,0,1],[4,2,0,1],[2,4,2,0,1],[1,4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[2,4,2,0,1],[4,2,0,1],[4,2,0,1],[1,4,2,0,1],[1,4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,4,2,0,1],[4,2,0,1],[1,4,2,0,1],[1,4,2,0,1],[4,2,0,1],[4,2,0,1],[2,4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[1,4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[1,4,2,0,1],[4,2,0,1]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[2,4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[1,4,2,0,1],[4,2,0,1],[4,2,0,1],[2,4,2,0,1],[1,4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[2,4,2,0,1],[4,2,0,1],[4,2,0,1],[1,4,2,0,1],[1,4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,4,2,0,1],[4,2,0,1],[1,4,2,0,1],[1,4,2,0,1],[4,2,0,1],[4,2,0,1],[2,4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[1,4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[4,2,0,1],[1,4,2,0,1],[4,2,0,1]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:14:39.400)
  (NBestInd 1)
  (utterance "remove 5 layers")
  (definition "repeat remove 5 times")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.repeat (number 5) (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove))
    )
  )
  (targetValue
    (string
      [[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[],[],[],[4],[],[],[],[],[],[],[2],[3],[],[],[],[],[3],[],[2],[3],[],[1],[],[],[4],[],[],[],[],[],[],[3],[],[],[],[],[],[],[],[2],[],[],[],[],[],[],[],[0],[0],[],[],[],[],[],[],[2],[],[],[0]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:14:54.602)
  (NBestInd 1)
  (utterance "add 5 red everywhere")
  (definition "repeat add red 5 times")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.repeat (number 5) (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 2 COLOR)))
    )
  )
  (targetValue
    (string
      [[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[4,2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2,2],[3,2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[3,2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2,2],[3,2,2,2,2,2],[2,2,2,2,2],[1,2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[4,2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[3,2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[0,2,2,2,2,2],[0,2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2,2],[2,2,2,2,2],[2,2,2,2,2],[0,2,2,2,2,2]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:16:25.118)
  (NBestInd 11)
  (utterance "add cyan every other one")
  (definition "add cyan if row %2= 1")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.filter (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.get (string col)) (string %=) (number 1))
        )
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 0 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:16:44.470)
  (NBestInd 0)
  (utterance "add red every other one")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.filter (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.get (string col)) (string %=) (number 1))
        )
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 2 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2],[],[0,2]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0],[],[0]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:17:43.508)
  (NBestInd 14)
  (utterance "add red to row 1")
  (definition "add red if row = 1")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.filter (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.get (string col)) (string >) (number 1))
        )
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 2 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[2],[0],[],[0],[],[0],[],[0],[2],[0],[],[0],[],[0],[],[0],[2],[0],[],[0],[],[0],[],[0],[2],[0],[],[0],[],[0],[],[0],[2],[0],[],[0],[],[0],[],[0],[2],[0],[],[0],[],[0],[],[0],[2],[0],[],[0],[],[0],[],[0],[2],[0],[],[0],[],[0],[],[0]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[2],[0],[],[0],[],[0],[],[0],[2],[0],[],[0],[],[0],[],[0],[2],[0],[],[0],[],[0],[],[0],[2],[0],[],[0],[],[0],[],[0],[2],[0],[],[0],[],[0],[],[0],[2],[0],[],[0],[],[0],[],[0],[2],[0],[],[0],[],[0],[],[0],[2],[0],[],[0],[],[0],[],[0]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:18:09.574)
  (NBestInd 21)
  (utterance "add cyan to row 5")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.filter (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.get (string col)) (string =) (number 5))
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 0 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:18:29.155)
  (NBestInd 4)
  (utterance "add brown to col 5")
   (definition "add brown if col = 5")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.filter (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.get (string row)) (string =) (number 5))
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 1 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0],[2,1],[0,1],[1],[0,1],[0,1],[0,1],[1],[0,1],[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0],[2,1],[0,1],[1],[0,1],[0,1],[0,1],[1],[0,1],[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0],[2],[0],[],[0],[0],[0],[],[0]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:20:13.041)
  (NBestInd 0)
  (utterance "add red to row 5")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.filter (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.get (string col)) (string =) (number 5))
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 2 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[2],[0],[],[0],[0,2],[0],[],[0],[2],[0],[],[0],[0,2],[0],[],[0],[2],[0],[],[0],[0,2],[0],[],[0],[2],[0],[],[0],[0,2],[0],[],[0],[2,1],[0,1],[1],[0,1],[0,1,2],[0,1],[1],[0,1],[2],[0],[],[0],[0,2],[0],[],[0],[2],[0],[],[0],[0,2],[0],[],[0],[2],[0],[],[0],[0,2],[0],[],[0]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[2],[0],[],[0],[0,2],[0],[],[0],[2],[0],[],[0],[0,2],[0],[],[0],[2],[0],[],[0],[0,2],[0],[],[0],[2],[0],[],[0],[0,2],[0],[],[0],[2,1],[0,1],[1],[0,1],[0,1,2],[0,1],[1],[0,1],[2],[0],[],[0],[0,2],[0],[],[0],[2],[0],[],[0],[0,2],[0],[],[0],[2],[0],[],[0],[0,2],[0],[],[0]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:20:22.774)
  (NBestInd 1)
  (utterance "add yellow to col 3")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.filter (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.get (string row)) (string =) (number 3))
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 4 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[2],[0],[],[0],[0,2],[0],[],[0],[2],[0],[],[0],[0,2],[0],[],[0],[2,4],[0,4],[4],[0,4],[0,2,4],[0,4],[4],[0,4],[2],[0],[],[0],[0,2],[0],[],[0],[2,1],[0,1],[1],[0,1],[0,1,2],[0,1],[1],[0,1],[2],[0],[],[0],[0,2],[0],[],[0],[2],[0],[],[0],[0,2],[0],[],[0],[2],[0],[],[0],[0,2],[0],[],[0]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[2],[0],[],[0],[0,2],[0],[],[0],[2],[0],[],[0],[0,2],[0],[],[0],[2,4],[0,4],[4],[0,4],[0,2,4],[0,4],[4],[0,4],[2],[0],[],[0],[0,2],[0],[],[0],[2,1],[0,1],[1],[0,1],[0,1,2],[0,1],[1],[0,1],[2],[0],[],[0],[0,2],[0],[],[0],[2],[0],[],[0],[0,2],[0],[],[0],[2],[0],[],[0],[0,2],[0],[],[0]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:20:52.093)
  (NBestInd 0)
  (utterance "remove 3 layers")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.repeat (number 3) (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove))
    )
  )
  (targetValue
    (string
      [[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:21:07.807)
  (NBestInd 0)
  (utterance "add 3 red everywhere")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.repeat (number 3) (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 2 COLOR)))
    )
  )
  (targetValue
    (string
      [[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:21:30.230)
  (NBestInd 5)
  (utterance "add 3 red on row 3")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.filter (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.get (string col)) (string =) (number 3))
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 2 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:22:48.296)
  (NBestInd 9)
  (utterance "add 3 yellow on col 3")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.repeat
        (number 3)
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.filter (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.get (string row)) (string =) (number 3))
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 4 COLOR))
        )
      )
    )
  )
  (targetValue
    (string
      [[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,4,4,4],[2,2,2,4,4,4],[2,2,2,2,4,4,4],[2,2,2,4,4,4],[2,2,2,4,4,4],[2,2,2,4,4,4],[2,2,2,4,4,4],[2,2,2,4,4,4],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,4,4,4],[2,2,2,4,4,4],[2,2,2,2,4,4,4],[2,2,2,4,4,4],[2,2,2,4,4,4],[2,2,2,4,4,4],[2,2,2,4,4,4],[2,2,2,4,4,4],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:26:09.889)
  (NBestInd 2)
  (utterance "add yellow then red")
  (definition "add yellow then add red")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.seq
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 4 COLOR))
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 2 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[2,2,2,4,2],[2,2,2,4,2],[2,2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,4,4,4,2],[2,2,2,4,4,4,4,2],[2,2,2,2,4,4,4,4,2],[2,2,2,4,4,4,4,2],[2,2,2,4,4,4,4,2],[2,2,2,4,4,4,4,2],[2,2,2,4,4,4,4,2],[2,2,2,4,4,4,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2],[2,2,2,4,2]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[1],[2],[],[],[],[],[],[],[],[],[],[],[],[],[3],[2],[0],[],[],[],[],[3],[],[],[],[],[],[1],[],[3],[],[],[],[],[1],[],[4],[],[],[],[],[],[3],[1],[],[],[],[4],[],[],[1],[],[4],[],[],[4],[],[],[3],[],[],[],[3],[]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:26:24.683)
  (NBestInd 2)
  (utterance "replace red by orange")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.has (number 2 COLOR))
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.seq
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 3 COLOR))
        )
      )
    )
  )
  (targetValue
    (string
      [[1],[3],[],[],[],[],[],[],[],[],[],[],[],[],[3],[3],[0],[],[],[],[],[3],[],[],[],[],[],[1],[],[3],[],[],[],[],[1],[],[4],[],[],[],[],[],[3],[1],[],[],[],[4],[],[],[1],[],[4],[],[],[4],[],[],[3],[],[],[],[3],[]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[1],[3],[],[],[],[],[],[],[],[],[],[],[],[],[3],[3],[0],[],[],[],[],[3],[],[],[],[],[],[1],[],[3],[],[],[],[],[1],[],[4],[],[],[],[],[],[3],[1],[],[],[],[4],[],[],[1],[],[4],[],[],[4],[],[],[3],[],[],[],[3],[]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:26:34.571)
  (NBestInd 8)
  (utterance "replace yellow by red")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.has (number 4 COLOR))
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.seq
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 2 COLOR))
        )
      )
    )
  )
  (targetValue
    (string
      [[1],[3],[],[],[],[],[],[],[],[],[],[],[],[],[3],[3],[0],[],[],[],[],[3],[],[],[],[],[],[1],[],[3],[],[],[],[],[1],[],[2],[],[],[],[],[],[3],[1],[],[],[],[2],[],[],[1],[],[2],[],[],[2],[],[],[3],[],[],[],[3],[]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[1],[3],[],[],[],[],[],[],[],[],[],[],[],[],[3],[3],[0],[],[],[],[],[3],[],[],[],[],[],[1],[],[3],[],[],[],[],[1],[],[2],[],[],[],[],[],[3],[1],[],[],[],[2],[],[],[1],[],[2],[],[],[2],[],[],[3],[],[],[],[3],[]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:26:55.018)
  (NBestInd 11)
  (utterance "replace red with brown")
  (definition "remove then add brown if top red")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.has (number 2 COLOR))
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.seq
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 1 COLOR))
        )
      )
    )
  )
  (targetValue
    (string
      [[1],[3],[],[],[],[],[],[],[],[],[],[],[],[],[3],[3],[0],[],[],[],[],[3],[],[],[],[],[],[1],[],[3],[],[],[],[],[1],[],[1],[],[],[],[],[],[3],[1],[],[],[],[1],[],[],[1],[],[1],[],[],[1],[],[],[3],[],[],[],[3],[]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[1],[3],[],[],[],[],[],[],[],[],[],[],[],[],[3],[3],[0],[],[],[],[],[3],[],[],[],[],[],[1],[],[3],[],[],[],[],[1],[],[2],[],[],[],[],[],[3],[1],[],[],[],[2],[],[],[1],[],[2],[],[],[2],[],[],[3],[],[],[],[3],[]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:27:20.869)
  (NBestInd 1)
  (utterance "add cyan to orange")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.has (number 3 COLOR)))
        )
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 0 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[1],[3,0],[],[],[],[],[],[],[],[],[],[],[],[],[3,0],[3,0],[0],[],[],[],[],[3,0],[],[],[],[],[],[1],[],[3,0],[],[],[],[],[1],[],[2],[],[],[],[],[],[3,0],[1],[],[],[],[2],[],[],[1],[],[2],[],[],[2],[],[],[3,0],[],[],[],[3,0],[]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[3],[3],[],[],[],[],[2],[],[3],[],[],[],[],[],[0],[],[],[],[3],[],[],[],[],[],[],[2],[3],[],[3],[],[],[],[],[],[],[4],[],[],[],[],[2],[2],[],[],[],[],[],[0],[],[],[],[],[],[],[],[1],[1],[],[],[]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:28:36.514)
  (NBestInd 7)
  (utterance "remove all but red")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.has (number 2 COLOR)))
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
      )
    )
  )
  (targetValue
    (string
      [[],[],[],[],[],[],[],[],[],[],[2],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[2],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[2],[2],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[],[],[],[],[],[2],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[2],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[2],[2],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:28:52.906)
  (NBestInd 1)
  (utterance "add 3 red")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.repeat (number 3) (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 2 COLOR)))
    )
  )
  (targetValue
    (string
      [[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2],[2,2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:29:18.939)
  (NBestInd 19)
  (utterance "add yellow if higher than 3")
  (definition "add yellow if height > 3")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.filter (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.get (string height)) (string >) (number 3))
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 4 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2,4],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2,4],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2,4],[2,2,2,2,4],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2,4],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2,4],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2,2,4],[2,2,2,2,4],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2],[2,2,2]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:32:21.822)
  (NBestInd 49)
  (utterance "remove 3 blocks if top is not yellow")
  (utterance "repeat remove 3 times if not top yellow")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.has (number 4 COLOR)))
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.repeat (number 3) (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove))
      )
    )
  )
  (targetValue
    (string
      [[],[],[],[],[],[],[],[],[],[],[2,2,2,2,4],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[2,2,2,2,4],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[2,2,2,2,4],[2,2,2,2,4],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[],[],[],[],[],[2,2,2,2,4],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[2,2,2,2,4],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[2,2,2,2,4],[2,2,2,2,4],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:32:52.391)
  (NBestInd 0)
  (utterance "add cyan if top is yellow")
  (utterance "add cyan if top yellow")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not
          (call
            edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not
            (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.has (number 4 COLOR)))
          )
        )
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 0 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[2,2,2,2,4],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[2,2,2,2,4],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[2,2,2,2,4],[2,2,2,2,4],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[2,2,2,2,4],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[2,2,2,2,4],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[2,2,2,2,4],[2,2,2,2,4],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0],[0]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:33:07.700)
  (NBestInd 1)
  (utterance "add 2 brown everywhere")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.repeat (number 2) (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 1 COLOR)))
    )
  )
  (targetValue
    (string
      [[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[2,2,2,2,4,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[2,2,2,2,4,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[2,2,2,2,4,1,1],[2,2,2,2,4,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[2,2,2,2,4,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[2,2,2,2,4,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[2,2,2,2,4,1,1],[2,2,2,2,4,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1],[0,1,1]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:33:53.767)
  (NBestInd 3)
  (utterance "remove all")
  (utterance "repeat remove many times")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.seq
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.seq
          (call
            edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.seq
            (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
            (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
          )
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
        )
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
      )
    )
  )
  (targetValue
    (string
      [[],[],[],[],[],[],[],[],[],[],[2,2,2],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[2,2,2],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[2,2,2],[2,2,2],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[],[],[],[],[],[2,2,2],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[2,2,2],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[2,2,2],[2,2,2],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:34:14.730)
  (NBestInd 2)
  (utterance "remove all")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.seq
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.seq
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
        )
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
      )
    )
  )
  (targetValue
    (string
      [[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:34:44.428)
  (NBestInd 3)
  (utterance "add red every other row 1")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.filter (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.get (string col)) (string %=) (number 1))
        )
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 2 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2],[],[2]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:35:03.247)
  (NBestInd 1)
  (utterance "add red to rows bigger than 1")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.filter (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.get (string col)) (string =) (number 1))
        )
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 2 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[],[2,2],[2],[2,2],[2],[2,2],[2],[2,2],[],[2,2],[2],[2,2],[2],[2,2],[2],[2,2],[],[2,2],[2],[2,2],[2],[2,2],[2],[2,2],[],[2,2],[2],[2,2],[2],[2,2],[2],[2,2],[],[2,2],[2],[2,2],[2],[2,2],[2],[2,2],[],[2,2],[2],[2,2],[2],[2,2],[2],[2,2],[],[2,2],[2],[2,2],[2],[2,2],[2],[2,2],[],[2,2],[2],[2,2],[2],[2,2],[2],[2,2]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[2,2],[2],[2,2],[2],[2,2],[2],[2,2],[],[2,2],[2],[2,2],[2],[2,2],[2],[2,2],[],[2,2],[2],[2,2],[2],[2,2],[2],[2,2],[],[2,2],[2],[2,2],[2],[2,2],[2],[2,2],[],[2,2],[2],[2,2],[2],[2,2],[2],[2,2],[],[2,2],[2],[2,2],[2],[2,2],[2],[2,2],[],[2,2],[2],[2,2],[2],[2,2],[2],[2,2],[],[2,2],[2],[2,2],[2],[2,2],[2],[2,2]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:35:26.640)
  (NBestInd 6)
  (utterance "add yellow to row bigger than 2")
  (definition "add yellow if row > 2")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.filter (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.get (string col)) (string <=) (number 2))
        )
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 4 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[],[2,2],[2,4],[2,2,4],[2,4],[2,2,4],[2,4],[2,2,4],[],[2,2],[2,4],[2,2,4],[2,4],[2,2,4],[2,4],[2,2,4],[],[2,2],[2,4],[2,2,4],[2,4],[2,2,4],[2,4],[2,2,4],[],[2,2],[2,4],[2,2,4],[2,4],[2,2,4],[2,4],[2,2,4],[],[2,2],[2,4],[2,2,4],[2,4],[2,2,4],[2,4],[2,2,4],[],[2,2],[2,4],[2,2,4],[2,4],[2,2,4],[2,4],[2,2,4],[],[2,2],[2,4],[2,2,4],[2,4],[2,2,4],[2,4],[2,2,4],[],[2,2],[2,4],[2,2,4],[2,4],[2,2,4],[2,4],[2,2,4]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[2,2],[2,4],[2,2,4],[2,4],[2,2,4],[2,4],[2,2,4],[],[2,2],[2,4],[2,2,4],[2,4],[2,2,4],[2,4],[2,2,4],[],[2,2],[2,4],[2,2,4],[2,4],[2,2,4],[2,4],[2,2,4],[],[2,2],[2,4],[2,2,4],[2,4],[2,2,4],[2,4],[2,2,4],[],[2,2],[2,4],[2,2,4],[2,4],[2,2,4],[2,4],[2,2,4],[],[2,2],[2,4],[2,2,4],[2,4],[2,2,4],[2,4],[2,2,4],[],[2,2],[2,4],[2,2,4],[2,4],[2,2,4],[2,4],[2,2,4],[],[2,2],[2,4],[2,2,4],[2,4],[2,2,4],[2,4],[2,2,4]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:35:42.562)
  (NBestInd 2)
  (utterance "add brown if shorter than 3")
  (definition "add brown if height < 3")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.filter (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.get (string height)) (string =) (number 3))
        )
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 1 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[1],[2,2,1],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[1],[2,2,1],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[1],[2,2,1],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[1],[2,2,1],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[1],[2,2,1],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[1],[2,2,1],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[1],[2,2,1],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[1],[2,2,1],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[2,4,1],[2,2,4]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[1],[2,2,1],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[1],[2,2,1],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[1],[2,2,1],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[1],[2,2,1],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[1],[2,2,1],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[1],[2,2,1],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[1],[2,2,1],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[1],[2,2,1],[2,4,1],[2,2,4],[2,4,1],[2,2,4],[2,4,1],[2,2,4]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:37:23.659)
  (NBestInd 0)
  (utterance "add 3 cyan everywhere")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.repeat (number 3) (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 0 COLOR)))
    )
  )
  (targetValue
    (string
      [[1,0,0,0],[2,2,1,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[1,0,0,0],[2,2,1,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[1,0,0,0],[2,2,1,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[1,0,0,0],[2,2,1,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[1,0,0,0],[2,2,1,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[1,0,0,0],[2,2,1,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[1,0,0,0],[2,2,1,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[1,0,0,0],[2,2,1,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[1,0,0,0],[2,2,1,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[1,0,0,0],[2,2,1,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[1,0,0,0],[2,2,1,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[1,0,0,0],[2,2,1,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[1,0,0,0],[2,2,1,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[1,0,0,0],[2,2,1,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[1,0,0,0],[2,2,1,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[1,0,0,0],[2,2,1,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0],[2,4,1,0,0,0],[2,2,4,0,0,0]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:37:33.520)
  (NBestInd 3)
  (utterance "remove all")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.seq
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.seq
          (call
            edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.seq
            (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
            (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
          )
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
        )
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
      )
    )
  )
  (targetValue
    (string
      [[],[2,2],[2,4],[2,2],[2,4],[2,2],[2,4],[2,2],[],[2,2],[2,4],[2,2],[2,4],[2,2],[2,4],[2,2],[],[2,2],[2,4],[2,2],[2,4],[2,2],[2,4],[2,2],[],[2,2],[2,4],[2,2],[2,4],[2,2],[2,4],[2,2],[],[2,2],[2,4],[2,2],[2,4],[2,2],[2,4],[2,2],[],[2,2],[2,4],[2,2],[2,4],[2,2],[2,4],[2,2],[],[2,2],[2,4],[2,2],[2,4],[2,2],[2,4],[2,2],[],[2,2],[2,4],[2,2],[2,4],[2,2],[2,4],[2,2]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[2,2],[2,4],[2,2],[2,4],[2,2],[2,4],[2,2],[],[2,2],[2,4],[2,2],[2,4],[2,2],[2,4],[2,2],[],[2,2],[2,4],[2,2],[2,4],[2,2],[2,4],[2,2],[],[2,2],[2,4],[2,2],[2,4],[2,2],[2,4],[2,2],[],[2,2],[2,4],[2,2],[2,4],[2,2],[2,4],[2,2],[],[2,2],[2,4],[2,2],[2,4],[2,2],[2,4],[2,2],[],[2,2],[2,4],[2,2],[2,4],[2,2],[2,4],[2,2],[],[2,2],[2,4],[2,2],[2,4],[2,2],[2,4],[2,2]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:37:37.435)
  (NBestInd 1)
  (utterance "remove all")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.seq
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
      )
    )
  )
  (targetValue
    (string
      [[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:38:47.313)
  (NBestInd 2)
  (utterance "add red to row greater than 5")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.filter (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.get (string col)) (string <=) (number 5))
        )
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 2 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[],[],[],[],[],[2],[2],[2],[],[],[],[],[],[2],[2],[2],[],[],[],[],[],[2],[2],[2],[],[],[],[],[],[2],[2],[2],[],[],[],[],[],[2],[2],[2],[],[],[],[],[],[2],[2],[2],[],[],[],[],[],[2],[2],[2],[],[],[],[],[],[2],[2],[2]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[2],[2],[2],[],[],[],[],[],[2],[2],[2],[],[],[],[],[],[2],[2],[2],[],[],[],[],[],[2],[2],[2],[],[],[],[],[],[2],[2],[2],[],[],[],[],[],[2],[2],[2],[],[],[],[],[],[2],[2],[2],[],[],[],[],[],[2],[2],[2]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:39:01.459)
  (NBestInd 11)
  (utterance "add red to col greater than 5")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.filter (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.get (string row)) (string <=) (number 5))
        )
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 2 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[],[],[],[],[],[2],[2],[2],[],[],[],[],[],[2],[2],[2],[],[],[],[],[],[2],[2],[2],[],[],[],[],[],[2],[2],[2],[],[],[],[],[],[2],[2],[2],[2],[2],[2],[2],[2],[2,2],[2,2],[2,2],[2],[2],[2],[2],[2],[2,2],[2,2],[2,2],[2],[2],[2],[2],[2],[2,2],[2,2],[2,2]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[2],[2],[2],[],[],[],[],[],[2],[2],[2],[],[],[],[],[],[2],[2],[2],[],[],[],[],[],[2],[2],[2],[],[],[],[],[],[2],[2],[2],[2],[2],[2],[2],[2],[2,2],[2,2],[2,2],[2],[2],[2],[2],[2],[2,2],[2,2],[2,2],[2],[2],[2],[2],[2],[2,2],[2,2],[2,2]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:39:43.252)
  (NBestInd 0)
  (utterance "add yellow if no red")
  (definition "add yellow if not has red")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not
          (call
            edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not
            (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.has (number 2 COLOR)))
          )
        )
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 4 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[4],[4],[4],[4],[4],[2],[2],[2],[4],[4],[4],[4],[4],[2],[2],[2],[4],[4],[4],[4],[4],[2],[2],[2],[4],[4],[4],[4],[4],[2],[2],[2],[4],[4],[4],[4],[4],[2],[2],[2],[2],[2],[2],[2],[2],[2,2],[2,2],[2,2],[2],[2],[2],[2],[2],[2,2],[2,2],[2,2],[2],[2],[2],[2],[2],[2,2],[2,2],[2,2]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[4],[4],[4],[4],[4],[2],[2],[2],[4],[4],[4],[4],[4],[2],[2],[2],[4],[4],[4],[4],[4],[2],[2],[2],[4],[4],[4],[4],[4],[2],[2],[2],[4],[4],[4],[4],[4],[2],[2],[2],[2],[2],[2],[2],[2],[2,2],[2,2],[2,2],[2],[2],[2],[2],[2],[2,2],[2,2],[2,2],[2],[2],[2],[2],[2],[2,2],[2,2],[2,2]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:40:17.942)
  (NBestInd 15)
  (utterance "add cyan if height greater than 2")
  (definition "add cyan if height > 2")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.iff
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.not
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.filter (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.get (string height)) (string <) (number 2))
        )
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.add (number 0 COLOR))
      )
    )
  )
  (targetValue
    (string
      [[4],[4],[4],[4],[4],[2],[2],[2],[4],[4],[4],[4],[4],[2],[2],[2],[4],[4],[4],[4],[4],[2],[2],[2],[4],[4],[4],[4],[4],[2],[2],[2],[4],[4],[4],[4],[4],[2],[2],[2],[2],[2],[2],[2],[2],[2,2,0],[2,2,0],[2,2,0],[2],[2],[2],[2],[2],[2,2,0],[2,2,0],[2,2,0],[2],[2],[2],[2],[2],[2,2,0],[2,2,0],[2,2,0]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 10)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[4],[4],[4],[4],[4],[2],[2],[2],[4],[4],[4],[4],[4],[2],[2],[2],[4],[4],[4],[4],[4],[2],[2],[2],[4],[4],[4],[4],[4],[2],[2],[2],[4],[4],[4],[4],[4],[2],[2],[2],[2],[2],[2],[2],[2],[2,2,0],[2,2,0],[2,2,0],[2],[2],[2],[2],[2],[2,2,0],[2,2,0],[2,2,0],[2],[2],[2],[2],[2],[2,2,0],[2,2,0],[2,2,0]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-10T11:40:47.495)
  (NBestInd 0)
  (utterance "remove all")
  (targetFormula
    (call
      context:edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.root
      (call
        edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.seq
        (call
          edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.seq
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
          (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
        )
        (call edu.stanford.nlp.sempre.cubeworld.RicherStacksWorld.remove)
      )
    )
  )
  (targetValue
    (string
      [[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 19)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[4],[],[],[],[],[],[],[],[3],[2],[],[],[2],[],[],[],[],[],[],[],[],[],[],[],[2],[],[],[0],[],[3],[],[],[0],[],[],[],[0],[],[],[],[],[0],[],[0],[],[],[],[],[],[],[],[],[],[4],[4],[0],[],[],[3]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-19T03:13:14.389)
  (NBestInd 0)
  (utterance "add red")
  (targetFormula (call context:root (call add (number 2 COLOR))))
  (targetValue
    (string
      [[2],[2],[2],[2],[2],[4,2],[2],[2],[2],[2],[2],[2],[2],[3,2],[2,2],[2],[2],[2,2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2,2],[2],[2],[0,2],[2],[3,2],[2],[2],[0,2],[2],[2],[2],[0,2],[2],[2],[2],[2],[0,2],[2],[0,2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[4,2],[4,2],[0,2],[2],[2],[3,2]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 19)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[2],[2],[2],[2],[2],[4,2],[2],[2],[2],[2],[2],[2],[2],[3,2],[2,2],[2],[2],[2,2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2,2],[2],[2],[0,2],[2],[3,2],[2],[2],[0,2],[2],[2],[2],[0,2],[2],[2],[2],[2],[0,2],[2],[0,2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[4,2],[4,2],[0,2],[2],[2],[3,2]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-19T03:13:39.869)
  (NBestInd 1)
  (utterance "replace red with yellow")
  (targetFormula (call context:root (call iff (call top (number 2 COLOR)) (call seq (call remove) (call add (number 4 COLOR))))))
  (targetValue
    (string
      [[4],[4],[4],[4],[4],[4,4],[4],[4],[4],[4],[4],[4],[4],[3,4],[2,4],[4],[4],[2,4],[4],[4],[4],[4],[4],[4],[4],[4],[4],[4],[4],[2,4],[4],[4],[0,4],[4],[3,4],[4],[4],[0,4],[4],[4],[4],[0,4],[4],[4],[4],[4],[0,4],[4],[0,4],[4],[4],[4],[4],[4],[4],[4],[4],[4],[4,4],[4,4],[0,4],[4],[4],[3,4]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 19)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[1],[2],[],[],[],[],[],[],[],[],[],[],[],[],[3],[2],[0],[],[],[],[],[3],[],[],[],[],[],[1],[],[3],[],[],[],[],[1],[],[4],[],[],[],[],[],[3],[1],[],[],[],[4],[],[],[1],[],[4],[],[],[4],[],[],[3],[],[],[],[3],[]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-19T03:18:42.747)
  (NBestInd 0)
  (utterance "add red")
  (targetFormula (call context:root (call add (number 2 COLOR))))
  (targetValue
    (string
      [[1,2],[2,2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[3,2],[2,2],[0,2],[2],[2],[2],[2],[3,2],[2],[2],[2],[2],[2],[1,2],[2],[3,2],[2],[2],[2],[2],[1,2],[2],[4,2],[2],[2],[2],[2],[2],[3,2],[1,2],[2],[2],[2],[4,2],[2],[2],[1,2],[2],[4,2],[2],[2],[4,2],[2],[2],[3,2],[2],[2],[2],[3,2],[2]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 19)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[3],[3],[],[],[],[],[2],[],[],[],[],[],[],[1],[],[],[],[],[2],[],[],[],[],[],[],[],[],[],[],[],[],[1],[],[],[],[],[],[],[],[],[2],[3],[1],[2],[4],[],[],[],[],[],[2],[4],[],[],[],[],[3],[1],[],[],[],[],[],[]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-19T03:21:12.640)
  (NBestInd 0)
  (utterance "add red")
  (targetFormula (call context:root (call add (number 2 COLOR))))
  (targetValue
    (string
      [[3,2],[3,2],[2],[2],[2],[2],[2,2],[2],[2],[2],[2],[2],[2],[1,2],[2],[2],[2],[2],[2,2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[1,2],[2],[2],[2],[2],[2],[2],[2],[2],[2,2],[3,2],[1,2],[2,2],[4,2],[2],[2],[2],[2],[2],[2,2],[4,2],[2],[2],[2],[2],[3,2],[1,2],[2],[2],[2],[2],[2],[2]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 19)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[3,2],[3,2],[2],[2],[2],[2],[2,2],[2],[2],[2],[2],[2],[2],[1,2],[2],[2],[2],[2],[2,2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[1,2],[2],[2],[2],[2],[2],[2],[2],[2],[2,2],[3,2],[1,2],[2,2],[4,2],[2],[2],[2],[2],[2],[2,2],[4,2],[2],[2],[2],[2],[3,2],[1,2],[2],[2],[2],[2],[2],[2]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-19T03:21:19.400)
  (NBestInd 0)
  (utterance "replace red with yellow")
  (targetFormula (call context:root (call iff (call top (number 2 COLOR)) (call seq (call remove) (call add (number 4 COLOR))))))
  (targetValue
    (string
      [[3,4],[3,4],[4],[4],[4],[4],[2,4],[4],[4],[4],[4],[4],[4],[1,4],[4],[4],[4],[4],[2,4],[4],[4],[4],[4],[4],[4],[4],[4],[4],[4],[4],[4],[1,4],[4],[4],[4],[4],[4],[4],[4],[4],[2,4],[3,4],[1,4],[2,4],[4,4],[4],[4],[4],[4],[4],[2,4],[4,4],[4],[4],[4],[4],[3,4],[1,4],[4],[4],[4],[4],[4],[4]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 19)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[3,4],[3,4],[4],[4],[4],[4],[2,4],[4],[4],[4],[4],[4],[4],[1,4],[4],[4],[4],[4],[2,4],[4],[4],[4],[4],[4],[4],[4],[4],[4],[4],[4],[4],[1,4],[4],[4],[4],[4],[4],[4],[4],[4],[2,4],[3,4],[1,4],[2,4],[4,4],[4],[4],[4],[4],[4],[2,4],[4,4],[4],[4],[4],[4],[3,4],[1,4],[4],[4],[4],[4],[4],[4]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-19T03:21:54.984)
  (NBestInd 0)
  (utterance "repeat remove many times")
  (targetFormula (call context:root (call repeat (number 10 infinity) (call remove))))
  (targetValue
    (string
      [[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 19)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-19T03:22:02.029)
  (NBestInd 0)
  (utterance "add red")
  (targetFormula (call context:root (call add (number 2 COLOR))))
  (targetValue
    (string
      [[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 19)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2],[2]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-19T03:22:14.116)
  (NBestInd 0)
  (utterance "remove all")
  (targetFormula (call context:root (call remove)))
  (targetValue
    (string
      [[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 19)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[1],[],[],[],[],[],[],[4],[2],[],[1],[],[3],[],[],[],[],[],[],[],[0],[],[],[],[],[],[],[],[3],[],[],[],[],[],[],[],[2],[],[2],[],[],[],[],[0],[],[0],[],[],[],[],[],[],[],[2],[],[],[],[],[],[0],[]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-19T04:01:30.281)
  (NBestInd 0)
  (utterance "add red")
  (targetFormula (call context:root (call add (number 2 COLOR))))
  (targetValue
    (string
      [[2],[2],[2],[1,2],[2],[2],[2],[2],[2],[2],[4,2],[2,2],[2],[1,2],[2],[3,2],[2],[2],[2],[2],[2],[2],[2],[0,2],[2],[2],[2],[2],[2],[2],[2],[3,2],[2],[2],[2],[2],[2],[2],[2],[2,2],[2],[2,2],[2],[2],[2],[2],[0,2],[2],[0,2],[2],[2],[2],[2],[2],[2],[2],[2,2],[2],[2],[2],[2],[2],[0,2],[2]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 19)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[4],[],[],[],[],[],[],[],[3],[2],[],[],[2],[],[],[],[],[],[],[],[],[],[],[],[2],[],[],[0],[],[3],[],[],[0],[],[],[],[0],[],[],[],[],[0],[],[0],[],[],[],[],[],[],[],[],[],[4],[4],[0],[],[],[3]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-19T11:13:17.183)
  (NBestInd 1)
  (utterance "replace red with yellow")
  (targetFormula (call context:root (call iff (call top (number 2 COLOR)) (call seq (call remove) (call add (number 4 COLOR))))))
  (targetValue
    (string
      [[],[],[],[],[],[4],[],[],[],[],[],[],[],[3],[4],[],[],[4],[],[],[],[],[],[],[],[],[],[],[],[4],[],[],[0],[],[3],[],[],[0],[],[],[],[0],[],[],[],[],[0],[],[0],[],[],[],[],[],[],[],[],[],[4],[4],[0],[],[],[3]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 19)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[4],[],[],[],[],[],[],[],[3],[4],[],[],[4],[],[],[],[],[],[],[],[],[],[],[],[4],[],[],[0],[],[3],[],[],[0],[],[],[],[0],[],[],[],[],[0],[],[0],[],[],[],[],[],[],[],[],[],[4],[4],[0],[],[],[3]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-19T11:14:00.730)
  (NBestInd 0)
  (utterance "add 3 cyan")
  (targetFormula (call context:root (call repeat (number 3 count) (call add (number 0 COLOR)))))
  (targetValue
    (string
      [[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[4,0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[3,0,0,0],[4,0,0,0],[0,0,0],[0,0,0],[4,0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[4,0,0,0],[0,0,0],[0,0,0],[0,0,0,0],[0,0,0],[3,0,0,0],[0,0,0],[0,0,0],[0,0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0,0],[0,0,0],[0,0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[0,0,0],[4,0,0,0],[4,0,0,0],[0,0,0,0],[0,0,0],[0,0,0],[3,0,0,0]]
    )
  )
)
(example
  (id session:sidaw)
  (context
    (date 2016 4 19)
    (graph
      NaiveKnowledgeGraph
      (
        (string
          [[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[],[]]
        )
        (name b)
        (name c)
      )
    )
  )
  (timeStamp 2016-05-19T15:33:21.456)
  (NBestInd 0)
  (utterance "repeat add yellow 3 times")
  (targetFormula (call context:root (call repeat (number 3 count) (call add (number 4 COLOR)))))
  (targetValue
    (string
      [[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4],[4,4,4]]
    )
  )
)
