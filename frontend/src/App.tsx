import React, { useState } from 'react'
import { BrowserRouter as Router, Routes, Route, Link } from 'react-router-dom'
import JobList from './JobList'
import JobDetail from './JobDetail'
import Triggers from './Triggers'
import Modal from './components/Modal'
import './App.css'

function App(): React.JSX.Element {
  const [showTriggers, setShowTriggers] = useState(false);

  return (
    <Router>
      <div className="App">
        <header className="navbar">
          <Link to="/" className="brand">Job Scheduler</Link>
          <nav className="nav-links">
            <Link to="/">Jobs</Link>
            <button className="button nav-button" onClick={(): void => setShowTriggers(true)}>
              Triggers
            </button>
          </nav>
        </header>
        <main className="container">
          <Routes>
            <Route path="/" element={<JobList />} />
            <Route path="/jobs/:id" element={<JobDetail />} />
          </Routes>
        </main>
        {showTriggers && (
          <Modal onClose={(): void => setShowTriggers(false)} title="Available Trigger Presets">
            <Triggers />
          </Modal>
        )}
      </div>
    </Router>
  )
}
export default App
