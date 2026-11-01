import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { UserProvider } from './context/UserContext';
import Navbar from './components/Navbar';
import BooksPage from './pages/BooksPage';
import MyLoansPage from './pages/MyLoansPage';
import MyFinesPage from './pages/MyFinesPage';
import LibrarianPage from './pages/LibrarianPage';

export default function App() {
  return (
    <UserProvider>
      <BrowserRouter>
        <Navbar />
        <main className="main-content">
          <Routes>
            <Route path="/" element={<Navigate to="/books" replace />} />
            <Route path="/books" element={<BooksPage />} />
            <Route path="/my-loans" element={<MyLoansPage />} />
            <Route path="/my-fines" element={<MyFinesPage />} />
            <Route path="/librarian" element={<LibrarianPage />} />
          </Routes>
        </main>
      </BrowserRouter>
    </UserProvider>
  );
}

