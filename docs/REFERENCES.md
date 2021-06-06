# References and algorithm lineage

Hazel implements established algorithms with original Clojure code. The
combination, interfaces, independent checks, and experiment artifacts are the
project's engineering work. No new theorem or new stochastic inference algorithm
is claimed.

* Munsky and Khammash, *The finite state projection algorithm for the solution of
  the chemical master equation*, Journal of Chemical Physics 124, 044104 (2006).
  [Author-hosted paper](https://www.engr.colostate.edu/~munsky/Papers/JChemPhys_124_044104.pdf)
  and [publication record](https://www.ccdc.ucsb.edu/publications/13596).
  This is the principal lineage for retaining probability lost beyond a finite
  state projection.
* Gillespie, *Exact stochastic simulation of coupled chemical reactions*,
  Journal of Physical Chemistry 81, 2340-2361 (1977).
  [Publisher DOI](https://doi.org/10.1021/j100540a008).
  This is the direct sample-path simulation lineage.
* Gibson and Bruck, *Efficient Exact Stochastic Simulation of Chemical Systems
  with Many Species and Many Channels*, Journal of Physical Chemistry A 104,
  1876-1889 (2000). [Publisher DOI](https://doi.org/10.1021/jp993732q).
  This is the dependency-aware next-reaction scheduling lineage.
* Andrieu, Doucet, and Holenstein, *Particle Markov chain Monte Carlo methods*,
  Journal of the Royal Statistical Society, Series B 72, 269-342 (2010).
  [Author-hosted paper](https://www.stats.ox.ac.uk/~doucet/andrieu_doucet_holenstein_PMCMC.pdf).
  This is the particle marginal Metropolis-Hastings lineage.
* [Clojure downloads and runtime dependencies](https://clojure.org/releases/downloads)
  and [dependency/CLI guide](https://clojure.org/guides/deps_and_cli).

The rational certificate construction is documented directly in
[CERTIFICATES.md](CERTIFICATES.md). Its proof obligation follows from positive
Taylor-series remainder bounds and nonnegative stochastic matrix products.
The checker is executable code with an explicitly stated trust base.

Other mechanisms use standard finite Markov-chain equations, polynomial algebra,
adaptive Runge-Kutta step doubling, inverse-BFGS updates, Jacobi diagonalization,
and Fisher-information criteria. Their exact conventions and restrictions are
specified in the numerical and inference contracts rather than implied by an
algorithm name.
